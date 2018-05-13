package com.gimc.mybatis.db.shard.router.rules;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisSqlActionRule;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.gimc.mybatis.db.shard.support.utils.CollectionUtils;

@Test
public class MyBatisSqlActionRuleTest {

    public static final String SQL_MAP_ACTION_ID = "com.alibaba.cobar.client.entity.Tweet.delete";
    public static final String[] EXPECTED_SHARDS = {"shard1", "shard2", "shard3"};

    private MyBatisSqlActionRule rule;

    @BeforeMethod
    protected void setUp() throws Exception {
        rule = new MyBatisSqlActionRule(SQL_MAP_ACTION_ID, "shard1, shard2, shard3");
    }

    @AfterMethod
    protected void tearDown() throws Exception {
        rule = null;
    }

    public void testSqlActionRuleOnShardIdsNormally() {
        List<String> shards = rule.action();
        assertTrue(CollectionUtils.isNotEmpty(shards));
        assertEquals(3, shards.size());

        for (String shard : shards) {
            assertTrue(ArrayUtils.contains(EXPECTED_SHARDS, shard));
        }
    }

    public void testSqlActionRuleOnShardIdsAbnormally() {
        try {
            new MyBatisSqlActionRule(SQL_MAP_ACTION_ID, "");
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            new MyBatisSqlActionRule(SQL_MAP_ACTION_ID, null);
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testSqlActionRuleOnShardIdsWithCustomActionPatternSeparatorNormally() {
        rule.setActionPatternSeparator(";");
        List<String> shards = rule.action();
        assertTrue(CollectionUtils.isNotEmpty(shards));
        assertEquals(1, shards.size());
        assertEquals("shard1, shard2, shard3", shards.get(0));

        rule = new MyBatisSqlActionRule(SQL_MAP_ACTION_ID, "shard1; shard2; shard3");
        rule.setActionPatternSeparator(";");
        shards = null;
        shards = rule.action();
        assertTrue(CollectionUtils.isNotEmpty(shards));
        assertEquals(3, shards.size());

        for (String shard : shards) {
            assertTrue(ArrayUtils.contains(EXPECTED_SHARDS, shard));
        }
    }

    public void testSqlActionRuleOnShardIdsWithCustomActionPatternSeparatorAbnormally() {
        try {
            rule.setActionPatternSeparator(null);
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testSqlActionRuleOnTypePatternNormally() {
        MyBatisRoutingFact fact = new MyBatisRoutingFact(SQL_MAP_ACTION_ID, null);
        assertTrue(rule.isDefinedAt(fact));

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.del", null);
        assertFalse(rule.isDefinedAt(fact));

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet", null);
        assertFalse(rule.isDefinedAt(fact));

        fact = new MyBatisRoutingFact(null, null);
        assertFalse(rule.isDefinedAt(fact));
    }

    public void testSqlActionRuleOnTypePatternAbnormally() {
        try {
            rule.isDefinedAt(null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }
}
