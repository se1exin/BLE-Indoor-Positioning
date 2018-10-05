package com.nexenio.bleindoorpositioning.ble.advertising;

public class TileAdvertisingPacketFactory extends AdvertisingPacketFactory {

    public TileAdvertisingPacketFactory() {
        this(TileAdvertisingPacket.class);
    }

    public <AP extends AdvertisingPacket> TileAdvertisingPacketFactory(Class<AP> packetClass) {
        super(packetClass);
    }

    @Override
    boolean canCreateAdvertisingPacket(byte[] advertisingData) {
        return TileAdvertisingPacket.meetsSpecification(advertisingData);
    }

    @Override
    AdvertisingPacket createAdvertisingPacket(byte[] advertisingData) {
        return new TileAdvertisingPacket(advertisingData);
    }
}
