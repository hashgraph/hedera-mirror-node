# Restore Importer database from a backup

## Problem

Consensus nodes have produced invalid data and the Importer has persisted the data. Restore the database to a previous point in time where the data was valid. The Importer will then read the corrected record stream files and persist the corrected data. 

## Setup
* Set the kubectl context to the correct cluster and namespace. 
* In a multicluster environment, perform these steps on one cluster (testnet-eu) in full before performing them on the other cluster (testnet-na).

## Procedure

1. Determine the number of Importer replicas. This is number of replicas that will be restored in step 7.

   ```shell
   kubectl describe deployment mirror-importer | grep Replicas:
   Replicas:           1 desired | 1 updated | 1 total | 1 available | 0 unavailable
   ```

2. Shut down the Monitor.
    
    ```shell
    kubectl scale --replicas=0 deployment/mirror-monitor
    ```  

3. Shut down the Importer pod(s) while monitoring the Importer logs.

   ```shell
   kubectl scale --replicas=0 deployment/mirror-importer
   ```

   The Importer logs show the shutdown and the last processed record file: 
   ```shell
   [pod/mirror-importer-54c477748d-nrsjm/importer] 2023-05-05T09:34:01.470-0600 INFO scheduling-4 c.h.m.i.p.r.RecordFileParser Successfully processed 169 items from 2023-05-05T15_33_58.979940142Z.rcd.gz in 26.32 ms
   [pod/mirror-importer-54c477748d-nrsjm/importer] 2023-05-05T09:34:01.895-0600 INFO Thread-6 c.h.m.i.u.ShutdownHelper Shutting down.......waiting 10s for internal processes to stop.
   ```

4. Verify that the database has stopped receiving data.

   ```shell
   mirror_node=> select max(consensus_end) from record_file;
    1683300839955833003      --> Friday, May 5, 2023 15:33:59.955
   ```

5. In the Google Console, restore the database from the backup.

6. Verify that the database has been restored to a prior point in time.

   ```shell
   mirror_node=> select max(consensus_end) from record_file;
    1683300509619621003      --> Friday, May 5, 2023 15:28:29.619
   ```

7. Restart the Importer Pod(s) using the number of replicas from step 1.

   ```shell
   kubectl scale --replicas=1 deployment/mirror-importer
   ```

8. Verify that the Importer is running and processing record files. The log should show the Importer beginning at the last process record file from the restored database.

   ```shell
   [pod/mirror-importer-54c477748d-mmxhm/importer] 2023-05-05T09:45:13.024-0600 INFO main c.h.m.i.MirrorImporterApplication Started MirrorImporterApplication in 17.413 seconds (JVM running for 18.874)
   [pod/mirror-importer-54c477748d-mmxhm/importer] 2023-05-05T09:45:13.177-0600 INFO scheduling-4 c.h.m.i.c.MirrorDateRangePropertiesProcessor RECORD: downloader will download files in time range (2023-05-05T15:28:28.044896003Z, 2262-04-11T23:47:16.854775807Z]
   [pod/mirror-importer-54c477748d-mmxhm/importer] 2023-05-05T09:45:13.177-0600 INFO scheduling-3 c.h.m.i.c.MirrorDateRangePropertiesProcessor BALANCE: downloader will download files in time range (2023-05-05T15:15:00.168045Z, 2262-04-11T23:47:16.854775807Z]
   ```
   
  9. Start the Monitor. 

      ```shell
      kubectl scale --replicas=1 deployment/mirror-monitor
      ```
     
  10. Perform these steps on the other cluster.