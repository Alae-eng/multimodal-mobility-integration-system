# Bridges (Membre A) : rail-bridge et air-bridge

Ces deux services cachent CORBA et SOAP derrière **le contrat REST commun**
(`contracts/operator-port.md`). Personne d'autre ne voit jamais un type CORBA ou SOAP :
c'est la couche anti-corruption du projet.

| Service | Port | Enveloppe | Legacy joint |
|---|---|---|---|
| `rail-bridge` | 8090 | CORBA (JacORB) → REST | `rail:1050`, IOR lu dans `ior/RailBooking.ior` |
| `air-bridge` | 8091 | SOAP (Spring WS) → REST | `http://air:8080/ws` |

Java 17, Spring Boot 3.2.5. Les sources `rail.idl` et `air.xsd` sont des **copies** : les dossiers
legacy ne sont jamais modifiés.

---

## 1. Démarrage

Le fragment `bridges/compose.bridges.yml` est inclus par le `docker-compose.yml` racine :

```yaml
include:
  - bridges/compose.bridges.yml
```

- À ajouter **après la fusion des deux PR** (rail puis air), sinon le build de l'air-bridge échoue.
- Pas de `depends_on` : le fragment doit rester valide seul. Les bridges tolèrent que `rail` et `air`
  démarrent après eux (connexion paresseuse).
- Les bridges mettent **15 à 25 secondes** à démarrer. Prévoir des essais au démarrage dans la
  passerelle ou la saga (ou un healthcheck, que je peux ajouter).
- Premier build long (téléchargement Maven). Construire **un service à la fois** :
  `docker compose build rail-bridge`, puis `docker compose build air-bridge`.
- Réseau : les bridges rejoignent le réseau par défaut du projet, nommé `mobilite-net` dans le compose racine.

URLs :

- Dans le réseau compose : `http://rail-bridge:8090` et `http://air-bridge:8091`
- Depuis l'hôte : `localhost:8090` et `localhost:8091`

---

## 2. Ce que la saga doit savoir

### Codes de retour

| Code | Sens | Action de la saga |
|---|---|---|
| 200 / 201 / 204 | succès | continuer |
| 409 `NOT_AVAILABLE` | refus métier : plus de places, hold inconnu ou expiré | **compenser** |
| 400 `BAD_REQUEST` | requête invalide (ex. `offerId` absent) | bug côté appelant, ne pas rejouer |
| 503 `UPSTREAM_UNAVAILABLE` | opérateur injoignable, délai dépassé, ou circuit ouvert | **ne pas confondre avec un refus** : réessayer plus tard |

Corps d'erreur : `{ "error": "...", "message": "..." }`. Les montants sont des entiers en centimes de MAD.

### Idempotence

- **`POST /v1/holds`** accepte l'en-tête `Idempotency-Key`. Même clé deux fois : même `holdId`, un seul
  hold chez l'opérateur. Utiliser **une clé unique par étape de saga**, par exemple `<sagaId>-rail`.
  - Mémoire du bridge : 30 minutes, **perdue au redémarrage du bridge**.
  - Un hold refusé (409) n'est pas mémorisé : le rejeu est réévalué.
  - La clé n'est pas liée au contenu de la requête.
- **`confirm` n'est PAS idempotent** : le legacy consomme le hold. Un confirm rejoué renvoie 409.
  Après un 503 ou un timeout sur un confirm, **le résultat est inconnu** : ne pas le rejouer à l'aveugle.
  Annuler avec le `holdId` ET avec le `bookingRef` éventuel.
- **`cancel`** renvoie toujours 204, même pour une référence inconnue ou déjà annulée, **sauf 503 si
  l'opérateur est injoignable**. Un 503 sur un cancel signifie que **la compensation n'est pas faite** :
  elle doit être réessayée. `cancel` accepte un `holdId` ou un `bookingRef`.

### Fenêtre de hold

Un hold expire après **10 minutes** chez les deux legacy. La phase de confirmation de la saga ne doit
pas dépasser cette fenêtre. Un hold orphelin immobilise des places jusqu'à expiration : c'est ce que la
compensation évite.

---

## 3. Résilience (déjà en place)

| Mécanisme | Valeur |
|---|---|
| Délai de connexion | 3 s (CORBA et SOAP) |
| Délai de réponse | 5 s (CORBA et SOAP) |
| Circuit breaker | un par opérateur (`rail`, `air`) |
| Ouverture | ≥ 50 % d'échecs techniques sur 10 appels, minimum 5 appels |
| Durée d'ouverture | 10 s, puis 2 appels d'essai (demi-ouvert) |
| Circuit ouvert | 503 immédiat, message `... circuit open` |

- Seuls les **échecs techniques** comptent. Un refus métier (409) n'ouvre jamais le circuit.
- Les timeouts de la saga doivent être **supérieurs à 6 s** (jusqu'à environ 10 s côté rail, voir plus bas).
- L'état des circuits est visible : `GET /actuator/health` sur chaque bridge
  (`CLOSED`, `OPEN`, `HALF_OPEN`). Pratique pour la démo.
- **Reconnexion CORBA** : si le serveur rail redémarre, il publie un nouvel IOR (nouvel identifiant d'objet).
  Le rail-bridge s'en aperçoit (`OBJECT_NOT_EXIST` ou `COMM_FAILURE`), relit l'IOR et réessaie **une seule
  fois**. Le nouvel essai n'a lieu que si c'est sans danger : toujours pour `search` et `cancel`, pour
  `hold` et `confirm` seulement si CORBA garantit que la requête n'a pas été exécutée. Un appel ainsi
  rattrapé peut durer jusqu'à environ 10 s.

---

## 4. Traçabilité

- La saga doit **envoyer l'en-tête `traceparent`** (W3C) à chaque appel vers les bridges. Sans lui,
  chaque bridge démarre sa propre trace et il n'y a pas de trace de bout en bout.
- Chaque ligne de log porte `[service, traceId, spanId]`. Exemple :
  `docker compose logs air-bridge | grep <traceId>`.
- Les bridges exportent en OTLP HTTP vers `http://otel-collector:4318/v1/traces`. Modifiable avec la
  variable d'environnement `OTLP_TRACES_ENDPOINT` : si le collecteur s'appelle autrement, le dire à A
  plutôt que modifier `compose.bridges.yml`.
- Noms de service : `rail-bridge`, `air-bridge`. Spans d'appel au legacy : `corba.rail.<op>`,
  `soap.air.<op>` (`search`, `hold`, `confirm`, `cancel`).
- **La trace s'arrête aux bridges** : les systèmes legacy ne propagent rien et ne sont pas modifiables.
- Tant que le collecteur n'existe pas, les logs affichent `UnknownHostException` (`HttpExporter`).
  C'est sans effet sur le fonctionnement.

---

## 5. Données de démo

| Cas | Appel |
|---|---|
| Trains (120 places chacun) | `GET :8090/v1/offers?origin=Casablanca&destination=Rabat&date=2026-11-15` → `RAIL-CAS-RAB-0730` (arrivée 08:25), `RAIL-CAS-RAB-0900` (arrivée 09:55) |
| Vol (40 places) | `GET :8091/v1/offers?origin=Rabat&destination=Paris&date=2026-11-15` → `AIR-RBA-ORY-1200` (départ 12:00) |
| **Échec forcé (air)** | `POST :8091/v1/holds` `{"offerId":"AIR-CMN-MAD-1800","seats":4}` → 409 (3 places seulement) |
| Échec forcé (rail) | `POST :8090/v1/holds` `{"offerId":"RAIL-CAS-RAB-0730","seats":9999}` → 409 |

Le champ `arrival` fait partie de l'`Offer` canonique : la saga peut vérifier qu'une correspondance est possible.

---

## 6. Tests

Chaque bridge a des tests rapides (contrat REST avec opérateur simulé, idempotence, mapping) et des tests
d'intégration contre le vrai kit. Ces derniers s'ignorent si le kit n'est pas joignable.

```bash
MSYS_NO_PATHCONV=1 docker run --rm --network mobilite-net \
  -v m2cache:/root/.m2 \
  -v "$(pwd -W)":/work -w /work/bridges/rail-bridge \
  maven:3.9-eclipse-temurin-17 mvn -B test
```

(Remplacer `rail-bridge` par `air-bridge` pour l'autre. Sous Linux, utiliser `"$(pwd)"` à la place de
`"$(pwd -W)"`.)

Attention : les tests d'intégration réservent puis annulent des places. **Ne pas les lancer pendant une
démo ou un test de saga**, car ils modifient les nombres de places.

---

## 7. Limites assumées (à citer dans le rapport)

- **Idempotence en mémoire** : perdue au redémarrage du bridge. Une base partagée la rendrait durable.
- **Le trace s'arrête aux bridges** : le legacy ne propage pas le contexte.
- **`confirm` non idempotent** : c'est le comportement du legacy, imposé et non modifiable.
- **Un circuit breaker par opérateur**, pas par opération : un cancel en échec technique peut ouvrir le
  circuit pour la recherche.
- Les appels `hold` et `confirm` qui expirent ont un résultat **inconnu**, et seule la compensation
  idempotente permet de revenir à un état sûr.

---

## 8. Propositions de modification du contrat (PR `contracts/`, 3 approbations)

1. Préciser dans `operator-port.md` que `cancel` renvoie **503 `UPSTREAM_UNAVAILABLE`** (et non 204) quand
   l'opérateur est injoignable. Le texte actuel dit « toujours 204 ».
2. Ajouter le code **400 `BAD_REQUEST`** à la liste des erreurs de la section 3.
3. Pour B : le premier jalon parlait de `GET /v1/search` pour l'autopartage, alors que le contrat dit
   `GET /v1/offers`. **Le contrat fait foi.**