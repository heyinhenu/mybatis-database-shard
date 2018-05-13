package com.gimc.mybatis.db.shard.router.rules;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisNamespaceRule;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import java.util.List;

import org.testng.annotations.Test;

import com.gimc.mybatis.db.shard.support.utils.CollectionUtils;

@Test
public class MyBatisNamespaceRuleTest {

    public void testNamespaceRuleNormally() {
        MyBatisNamespaceRule rule = new MyBatisNamespaceRule("com.alibaba.cobar.client.entity.Tweet", "p1, p2");
        List<String> shardIds = rule.action();
        assertNotNull(shardIds);
        assertEquals(2, shardIds.size());

        MyBatisRoutingFact fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.update", null);
        assertTrue(rule.isDefinedAt(fact));
        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.delete", null);
        assertTrue(rule.isDefinedAt(fact));
        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Twet.delete", null);
        assertFalse(rule.isDefinedAt(fact));
    }

    public void testNamespaceRuleNormallyWithCustomActionPatternSeparator() {
        MyBatisNamespaceRule rule = new MyBatisNamespaceRule("com.alibaba.cobar.client.entity.Tweet", "p1, p2");
        rule.setActionPatternSeparator(";");
        List<String> shards = rule.action();
        assertTrue(CollectionUtils.isNotEmpty(shards));
        assertEquals(1, shards.size());

        rule = new MyBatisNamespaceRule("com.alibaba.cobar.client.entity.Tweet", "p1; p2");
        rule.setActionPatternSeparator(";");
        shards = null;
        shards = rule.action();
        assertTrue(CollectionUtils.isNotEmpty(shards));
        assertEquals(2, shards.size());

        MyBatisRoutingFact fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.update", null);
        assertTrue(rule.isDefinedAt(fact));
        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.delete", null);
        assertTrue(rule.isDefinedAt(fact));
        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Twet.delete", null);
        assertFalse(rule.isDefinedAt(fact));
    }

    public void testNamespaceRuleAbnormally() {
        try {
            new MyBatisNamespaceRule("", "");
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            new MyBatisNamespaceRule("", null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            new MyBatisNamespaceRule(null, "");
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            new MyBatisNamespaceRule(null, null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        MyBatisNamespaceRule rule = new MyBatisNamespaceRule("com.alibaba.cobar.client.entity.Tweet", "p1, p2");
        try {
            rule.setActionPatternSeparator(null);
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            rule.isDefinedAt(null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }
}
