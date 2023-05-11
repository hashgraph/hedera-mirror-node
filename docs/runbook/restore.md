# Restore Importer database from a backup

## Problem

Consensus nodes have produced invalid data and the Importer has persisted the data. Restore the database to a previous point in time where the data was valid. The Importer will then read the corrected record stream files and persist the corrected data. 

## Setup
* Set the kubectl context to the correct cluster and namespace. 
* In a multicluster environment, perform these steps on one cluster (testnet-eu) in full before performing them on the other cluster (testnet-na).


## Procedure

1. Shut down the Monitor to stop the load balancer from routing requests to the cluster.
    
    ```shell
    kubectl scale --replicas=0 deployment/mirror-monitor
    ```  

2. Shut down the Importer pod while monitoring the Importer logs.

   ```shell
   kubectl scale --replicas=0 deployment/mirror-importer
   ```

   The Importer logs show the shutdown and the last processed record file: 
   ```shell
   [pod/mirror-importer-54c477748d-mmxhm/importer] 2023-05-10T10:38:31.723-0600 INFO scheduling-3 c.h.m.i.p.r.RecordFileParser Successfully processed 174 items from 2023-05-10T16_38_28.618260003Z.rcd.gz in 23.11 ms
   [pod/mirror-importer-54c477748d-mmxhm/importer] 2023-05-10T10:38:31.896-0600 INFO Thread-6 c.h.m.i.u.ShutdownHelper Shutting down.......waiting 10s for internal processes to stop.
   ```

3. Verify that the Importer has stopped persisting data by querying the REST API multiple times. The record file name should stop changing and show the name of the last processed record file:

   ```shell
    curl -sL "https://<hostname>/api/v1/blocks?limit=1&order=desc" | jq -r '.blocks[0].name'
    2023-05-10T16_38_28.618260003Z.rcd.gz
   ``` 

4. In the Google Console, restore the database from the backup. Restore to a backup that was created before the invalid data was persisted. 

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[<img src="backup1.png" width="250"/>](backup1.png "Google Cloud Backups")
[<img src="backup2.png" width="250"/>](backup2.png "Restore from Backup")

5. After the Restore process has completed verify that the database has been restored by running the command from step 3, it should show that the record file is now at a point in time prior to the invalid data.

6. Restart all pods to clear their cache. The command below will delete all pods in the current namespace.
 
   ```shell
   kubectl delete --all pods
   
   pod "mirror-graphql-7784c9f798-c4zzj" deleted
   pod "mirror-graphql-7784c9f798-f7q8f" deleted
   pod "mirror-grpc-68c794678d-n7xk5" deleted
   pod "mirror-grpc-68c794678d-pkkqj" deleted
   pod "mirror-rest-64fb7f4fb7-7dclv" deleted
   pod "mirror-rest-64fb7f4fb7-lf4st" deleted
   pod "mirror-rest-monitor-cdb8b4fcd-fkc54" deleted
   pod "mirror-rosetta-6d4f6b798f-bs7vb" deleted
   pod "mirror-rosetta-6d4f6b798f-hjhjf" deleted
   pod "mirror-web3-7b5444dcb7-lhr5c" deleted
   pod "mirror-web3-7b5444dcb7-ps45v" deleted
    ```

7. Restart the Importer pod.

   ```shell
   kubectl scale --replicas=1 deployment/mirror-importer
   ```

8. Verify that the Importer is running and processing record files. The log should show the Importer beginning from the last process record file from the restored database.

   ```shell
   [pod/mirror-importer-54c477748d-9kdxr/importer] 2023-05-10T11:04:40.766-0600 INFO main c.h.m.i.MirrorImporterApplication Started MirrorImporterApplication in 16.723 seconds (JVM running for 18.504)
   [pod/mirror-importer-54c477748d-9kdxr/importer] 2023-05-10T11:04:40.945-0600 INFO scheduling-4 c.h.m.i.c.MirrorDateRangePropertiesProcessor RECORD: downloader will download files in time range (2023-05-05T15:28:28.044896003Z, 2262-04-11T23:47:16.854775807Z]
   [pod/mirror-importer-54c477748d-9kdxr/importer] 2023-05-10T11:04:40.945-0600 INFO scheduling-3 c.h.m.i.c.MirrorDateRangePropertiesProcessor BALANCE: downloader will download files in time range (2023-05-05T15:15:00.168045Z, 2262-04-11T23:47:16.854775807Z]
   ```
   
9. Start the Monitor. 

    ```shell
    kubectl scale --replicas=1 deployment/mirror-monitor
    ```
10. Get the names of the REST pods for use in step 11.

    ```shell
    kubectl get pod -o name | grep rest
    pod/mirror-rest-bb68f656-j6bwd
    pod/mirror-rest-bb68f656-vqb8g
    ```

11. Watch the logs of the REST pods while querying the REST API multiple times with the command from step 3. 

- Verify that traffic is being routed to the cluster by looking for the pod name in the logs.
- Verify that the record file is at a point in time prior to the date of the invalid data.


   The REST pods logs should show that the query is being routed to the cluster: 
   ```shell
   [pod/mirror-rest-bb68f656-j6bwd/rest] 2023-05-10T11:10:10.134Z INFO 06534719 GET /api/v1/blocks?limit=1 in 5 ms: 200
   [pod/mirror-rest-bb68f656-vqb8g/rest] 2023-05-10T11:10:17.183Z INFO 194ee756 GET /api/v1/blocks?limit=1 in 4 ms: 200
   ```

11. Perform these steps on the other cluster.