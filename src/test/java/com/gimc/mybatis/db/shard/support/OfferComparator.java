package com.gimc.mybatis.db.shard.support;

import java.util.Comparator;

import com.gimc.mybatis.db.shard.entities.Offer;

public class OfferComparator implements Comparator<Offer> {

    public int compare(Offer offer1, Offer offer2) {
        return offer1.getSubject().compareTo(offer2.getSubject());
    }

}
