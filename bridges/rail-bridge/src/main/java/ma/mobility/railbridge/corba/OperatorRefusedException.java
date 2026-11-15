package ma.mobility.railbridge.corba;

/** Refus métier de l'opérateur (CORBA NotAvailable) -> HTTP 409 NOT_AVAILABLE. */
public class OperatorRefusedException extends RuntimeException {
    public OperatorRefusedException(String reason) {
        super(reason);
    }
}