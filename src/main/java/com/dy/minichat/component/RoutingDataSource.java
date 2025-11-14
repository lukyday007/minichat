package com.dy.minichat.component;

import com.dy.minichat.property.DataSourceProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {
    private final MasterReplicaKeyRouter masterReplicaKeyRouter;

    public RoutingDataSource(Map<Object, Object> dataSourceMap, MasterReplicaKeyRouter masterReplicaKeyRouter) {
        // System.out.println("-------------->> RoutingDataSource RoutingDataSource");
        this.masterReplicaKeyRouter = masterReplicaKeyRouter;
        super.setTargetDataSources(dataSourceMap);
        this.afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        Object key;
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            // System.out.println("[SLAVE TX]");
            key = masterReplicaKeyRouter.getReplicaKey();
            //System.out.println("     └─ determineCurrentLookupKey → " + key);
        } else {
            // System.out.println("[MASTER TX]");
            key= masterReplicaKeyRouter.getMasterKey();
            // System.out.println("     └─ determineCurrentLookupKey → " + key);
        }
        // System.out.println("DATASOURCE: " + key);
        return key;
    }
}