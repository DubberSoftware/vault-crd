package de.koudingspawn.vault.kubernetes.scheduler;

import de.koudingspawn.vault.crd.DoneableVault;
import de.koudingspawn.vault.crd.Vault;
import de.koudingspawn.vault.crd.VaultList;
import de.koudingspawn.vault.kubernetes.EventHandler;
import de.koudingspawn.vault.kubernetes.event.EventNotification;
import de.koudingspawn.vault.vault.communication.SecretNotAccessibleException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import static de.koudingspawn.vault.kubernetes.event.EventType.MODIFICATION_FAILED;

@Component
@Profile("!test")
public class ScheduledRefresh {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRefresh.class);

    private final TypeRefreshFactory typeRefreshFactory;
    private final EventHandler eventHandler;
    private final EventNotification eventNotification;
    private final MixedOperation<Vault, VaultList, DoneableVault, Resource<Vault, DoneableVault>> customResource;

    public ScheduledRefresh(
            EventHandler eventHandler,
            TypeRefreshFactory typeRefreshFactory,
            EventNotification eventNotification, MixedOperation<Vault, VaultList, DoneableVault, Resource<Vault, DoneableVault>> customResource) {
        this.typeRefreshFactory = typeRefreshFactory;
        this.eventHandler = eventHandler;
        this.eventNotification = eventNotification;
        this.customResource = customResource;
    }

    @Autowired
    private Environment env;

    @Scheduled(fixedRateString = "${kubernetes.interval}000", initialDelayString = "${kubernetes.initial-delay}000")
    public void refreshCertificates() {
        log.info("Start refresh of secret...");

        String watchedNamespace = env.getProperty("kubernetes.watched.namespace","");
        if(watchedNamespace=="") {
            watchedNamespace=null;
        }
        String watchedLabel = env.getProperty("kubernetes.watched.instancelabel","");
        Map<String, String> instanceLabel = new HashMap<>();
        if(watchedLabel!="") {
            instanceLabel.put("instance", watchedLabel);
        }

        VaultList list = customResource.inNamespace(watchedNamespace).withLabels(instanceLabel).list();
        for (Vault resource : list.getItems()) {
            RequiresRefresh requiresRefresh = typeRefreshFactory.get(resource.getSpec().getType().toString());
            try {
                if (requiresRefresh.refreshIsNeeded(resource)) {
                    eventHandler.modifyHandler(resource);
                }
            } catch (SecretNotAccessibleException e) {
                log.info("Refresh of secret {} in namespace {} failed with exception", resource.getMetadata().getName(), resource.getMetadata().getNamespace(), e);

                eventNotification.storeNewEvent(MODIFICATION_FAILED, "Modification of secret failed with exception " + e.getMessage(), resource);
            }
        }

        log.info("Finished refresh of secret...");

    }

}
