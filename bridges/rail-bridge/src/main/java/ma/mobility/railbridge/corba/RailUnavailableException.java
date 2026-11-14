package ma.mobility.railbridge.corba;

/** Opérateur ferroviaire injoignable (IOR illisible, serveur éteint...). */
public class RailUnavailableException extends RuntimeException {
    public RailUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}