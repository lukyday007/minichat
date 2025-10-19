package com.dy.minichat.config.replica;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {
    private final MasterReplicaKeyRouter masterReplicaKeyRouter;

    // key: database -> dataSourceMap, algorithm -> masterReplicaKeyRouter
    public RoutingDataSource(Map<Object, Object> dataSourceMap, MasterReplicaKeyRouter masterReplicaKeyRouter) {

        this.masterReplicaKeyRouter = masterReplicaKeyRouter;
        super.setTargetDataSources(dataSourceMap);
        this.afterPropertiesSet();
    }

    // read - replica / write - master
    @Override
    protected Object determineCurrentLookupKey() {

        Object key;
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            System.out.println("[SLAVE TX]");
            key = masterReplicaKeyRouter.getReplicaKey();
            System.out.println("     └─ determineCurrentLookupKey → " + key);

        } else {
            System.out.println("[MASTER TX]");
            key= masterReplicaKeyRouter.getMasterKey();
            System.out.println("     └─ determineCurrentLookupKey → " + key);
        }

        System.out.println("DATASOURCE: " + key);
        return key;
    }
}