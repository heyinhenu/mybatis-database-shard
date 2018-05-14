package com.gimc.mybatis.db.shard;

import java.sql.SQLException;
import java.util.List;
import org.apache.ibatis.session.SqlSession;

public interface SqlSessionCallBack {

    Object execute(SqlSession session) throws SQLException;
}
