package co.zeal.marketplace.shared.events;

/** Puerto de salida hacia el Message Broker. */
public interface IntegrationEventPublisher {

    void publish(IntegrationEvent event);
}
