package ma.mobility.airbridge.soap;

/** Refus métier du partenaire (SOAP Fault NotAvailable) -> HTTP 409 NOT_AVAILABLE. */
public class OperatorRefusedException extends RuntimeException {
    public OperatorRefusedException(String reason) {
        super(reason);
    }
}