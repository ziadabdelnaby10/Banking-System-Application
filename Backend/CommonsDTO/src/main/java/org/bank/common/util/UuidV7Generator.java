package org.bank.common.util;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

public class UuidV7Generator {
    //native implementation
//    public static String generateUuidV7() {
//        long currentTimeMillis = System.currentTimeMillis();
//        long timeHigh = (currentTimeMillis & 0xFFFFFFFFFFFFL) << 16;
//        long timeLow = (currentTimeMillis & 0xFFFFL) << 48;
//        long randomBits = (long) (Math.random() * 0xFFFFFFFFFFFFL);
//        long uuidMostSigBits = timeHigh | timeLow | randomBits;
//        long uuidLeastSigBits = (long) (Math.random() * 0xFFFFFFFFFFFFFFFFL);
//        return new java.util.UUID(uuidMostSigBits, uuidLeastSigBits).toString();
//    }

    public static UUID generateUUIDV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }

}
