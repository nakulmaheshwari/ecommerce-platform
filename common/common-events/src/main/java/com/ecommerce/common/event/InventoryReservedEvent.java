package com.ecommerce.common.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonTypeName("inventory.reserved")
public class InventoryReservedEvent extends DomainEvent {
    public static final String TOPIC = "inventory-reserved";
    public static final String VERSION = "1.0";

    private UUID orderId;
    private List<ReservedItem> reservedItems;

    public record ReservedItem(String skuId, Integer quantity) {}
}
