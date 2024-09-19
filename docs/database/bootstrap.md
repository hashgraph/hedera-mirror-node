# Database Bootstrap Guide

This guide provides step-by-step instructions for setting up a fresh PostgreSQL database and importing Mirror Node data into it. The process involves initializing the database, configuring environment variables, and running the import script. The data import is a long-running process, so it's recommended to run it within a `screen` or `tmux` session.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Database Initialization](#database-initialization)
  - [1. Configure Environment Variables](#1-configure-environment-variables)
  - [2. Important Note for Google Cloud SQL Users](#2-important-note-for-google-cloud-sql-users)
  - [3. Run the Initialization Script](#3-run-the-initialization-script)
  - [4. Import the Database Schema](#4-import-the-database-schema)
- [Data Import Process](#data-import-process)
  - [1. Download the Database Export Data](#1-download-the-database-export-data)
  - [2. Download the Import Script](#2-download-the-import-script)
  - [3. Run the Import Script](#3-run-the-import-script)
- [Mirror Node Version Compatibility](#mirror-node-version-compatibility)
- [Handling Failed Imports](#handling-failed-imports)
  - [Steps to Handle Failed Imports:](#steps-to-handle-failed-imports)
- [Additional Notes](#additional-notes)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

- **PostgreSQL 16** installed and running.
- Access to a machine where you can run the initialization and import scripts and connect to the PostgreSQL database.
- A Google Cloud Platform (GCP) account with a valid billing account attached (required for downloading data from a Requester Pays bucket).

---

## Database Initialization

### 1. Configure Environment Variables

Set the following environment variables on the machine from which you will run the initialization and import scripts. These variables allow for database connectivity and authentication.

**Database Connection Variables:**

```bash
export PGUSER="postgres"
export PGPASSWORD="YOUR_POSTGRES_PASSWORD"
export PGDATABASE="postgres"
export PGHOST="DB_IP_ADDRESS"
export PGPORT="DB_PORT"
```

- `PGUSER`: The PostgreSQL superuser with administrative privileges (typically `postgres`).
- `PGPASSWORD`: Password for the PostgreSQL superuser.
- `PGDATABASE`: The default database to connect to (`postgres` by default).
- `PGHOST`: The IP address or hostname of your PostgreSQL database server.
- `PGPORT`: The database server port number (`5432` by default).

**Database User Password Variables:**

Set the following environment variables to define passwords for the various database users that will be created during initialization.

```bash
export GRAPHQL_PASSWORD="SET_PASSWORD"
export GRPC_PASSWORD="SET_PASSWORD"
export IMPORTER_PASSWORD="SET_PASSWORD"
export OWNER_PASSWORD="SET_PASSWORD"
export REST_PASSWORD="SET_PASSWORD"
export REST_JAVA_PASSWORD="SET_PASSWORD"
export ROSETTA_PASSWORD="SET_PASSWORD"
export WEB3_PASSWORD="SET_PASSWORD"
```

- Replace `SET_PASSWORD` with strong, unique passwords for each respective user.

### 2. Important Note for Google Cloud SQL Users

If you are using **Google Cloud SQL** for your PostgreSQL database, an additional step is required before running the `init.sh` script to ensure proper initialization.

**Add the Following Line to the Initialization Script:**

Before running the `init.sh` script, you need to grant the `mirror_node` role to the `postgres` user. This is necessary because Google Cloud SQL restricts certain permissions for the `postgres` user.

Add the following line **before** running the `init.sh` script:

```sql
GRANT mirror_node TO postgres;
```

**Revised Section of `init.sh`:**

```sql
-- Create database & owner
CREATE USER :ownerUsername WITH LOGIN PASSWORD :'ownerPassword';
GRANT mirror_node TO postgres;
CREATE DATABASE :dbName WITH OWNER :ownerUsername;
```

- This adjustment ensures that the `postgres` user has the necessary permissions to execute the initialization script correctly on Google Cloud SQL.

### 3. Run the Initialization Script

Download the initialization script `init.sh` from the repository:

```bash
curl -O https://raw.githubusercontent.com/hashgraph/hedera-mirror-node/main/hedera-mirror-importer/src/main/resources/db/scripts/init.sh
chmod +x init.sh
```

Run the initialization script:

```bash
./init.sh
echo "EXIT STATUS: $?"
```

- The exit status `0` indicates the script executed successfully.
- The script will create the `mirror_node` database, along with all necessary roles, users, and permissions within your PostgreSQL database, using the passwords specified in the environment variables.

### 4. Import the Database Schema

After the initialization script completes successfully, update the environment variables to connect using the `mirror_node` user and database:

```bash
export PGUSER="mirror_node"
export PGPASSWORD="$OWNER_PASSWORD"  # Use the password set for OWNER_PASSWORD
export PGDATABASE="mirror_node"
```

Import the database schema:

```bash
psql -f schema.sql
echo "EXIT STATUS: $?"
```

- Ensure the exit status is `0` to confirm the schema was imported successfully.

---

## Data Import Process

### 1. Download the Database Export Data

The Mirror Node database export data is available in a Google Cloud Storage (GCS) bucket:

- **Bucket URL:** [mirrornode-db-export](https://console.cloud.google.com/storage/browser/mirrornode-db-export)

**Important Notes:**

- The bucket is **read-only** to the public.
- It is configured as **Requester Pays**, meaning you need a GCP account with a valid billing account attached to download the data.
- You will be billed for the data transfer fees incurred during the download.

**Download Instructions:**

1. **Authenticate with GCP:**

   Ensure you have the [Google Cloud SDK](https://cloud.google.com/sdk/docs/install) installed and authenticated:

   ```bash
   gcloud auth login
   gcloud config set billing/disable_usage_reporting false
   ```

2. **Set the Default Project:**

   ```bash
   gcloud config set project YOUR_GCP_PROJECT_ID
   ```

3. **Download the Data:**

   Create an empty directory to store the data and download all files and subdirectories:

   ```bash
   mkdir -p /path/to/db_export
   gsutil -u YOUR_GCP_PROJECT_ID -m cp -r gs://mirrornode-db-export/* /path/to/db_export/
   ```

   - Replace `/path/to/db_export` with your desired directory path.
   - Ensure all files and subdirectories are downloaded into this single parent directory.
   - **Note:** The `-m` flag enables parallel downloads to speed up the process.

### 2. Download the Import Script

Download the import script `bootstrap.sh` from the repository:

```bash
curl -O https://raw.githubusercontent.com/hashgraph/hedera-mirror-node/main/hedera-mirror-importer/src/main/resources/db/scripts/bootstrap.sh
chmod +x bootstrap.sh
```

### 3. Run the Import Script

The import script is designed to efficiently import the Mirror Node data into your PostgreSQL database. It handles compressed CSV files and uses parallel processing to speed up the import.

**Script Summary:**

- **Name:** `bootstrap.sh`
- **Functionality:** Imports data from compressed CSV files into the PostgreSQL database using parallel processing. It processes multiple tables concurrently based on the number of CPU cores specified.
- **Requirements:** Ensure that the environment variables for database connectivity are set (`PGUSER`, `PGPASSWORD`, `PGDATABASE`, `PGHOST`).

**Instructions:**

1. **Ensure Environment Variables are Set:**

   The environment variables should still be set from the previous steps. Verify them:

   ```bash
   echo $PGUSER     # Should output 'mirror_node'
   echo $PGPASSWORD # Should output the password you set for OWNER_PASSWORD
   echo $PGDATABASE # Should output 'mirror_node'
   echo $PGHOST     # Should be set to your DB IP address
   ```

2. **Run the Import Script within a `screen` or `tmux` Session:**

   It's recommended to run the import script within a `screen` or `tmux` session, as the import process may take several hours to complete.

   **Using `screen`:**

   ```bash
   screen -S db_import
   ```

   **Run the Import Script:**

   ```bash
   ./bootstrap.sh 8 /path/to/db_export/
   ```

   - `8` refers to the number of CPU cores to use for parallel processing. Adjust this number based on your system's resources.
   - `/path/to/db_export/` is the directory where you downloaded the database export data.

   **Detach from the `screen` Session:**

   Press `Ctrl+A` then `D`.

   - This allows the import process to continue running in the background.

   **Reattach to the `screen` Session Later:**

   ```bash
   screen -r db_import
   ```

3. **Monitor the Import Process:**

   - The script will output logs indicating the progress of the import.
   - Check the `import.log` file for detailed logs and any error messages.

4. **Check the Exit Status:**

   After the script completes, check the exit status:

   ```bash
   echo "EXIT STATUS: $?"
   ```

   - An exit status of `0` indicates the import completed successfully.
   - If the exit status is not `0`, refer to the `import.log` file and `import_tracking.txt` for troubleshooting.

---

## Mirror Node Version Compatibility

Before initializing your Mirror Node with the imported database, it's crucial to ensure version compatibility.

**MIRRORNODE_VERSION File:**

- In the database export data, there is a file named `MIRRORNODE_VERSION`.
- This file contains the version of the Mirror Node at the time of the database export.

**Importance:**

- Your Mirror Node instance must be initialized with the **same version** as specified in the `MIRRORNODE_VERSION` file.
- Using a different version may lead to compatibility issues and/or schema mismatches.

**Action Required:**

1. **Check the Mirror Node Version:**

   - Open the `MIRRORNODE_VERSION` file:

     ```bash
     cat /path/to/db_export/MIRRORNODE_VERSION
     ```

   - Note the version number specified.

---

## Handling Failed Imports

During the import process, the script generates a file named `import_tracking.txt`, which logs the status of each file import. Each line in this file contains the path and name of a file, followed by its import status: `NOT_STARTED`, `IN_PROGRESS`, `IMPORTED`, or `FAILED_TO_IMPORT`.

**Statuses:**

- `NOT_STARTED`: The file has not yet been processed.
- `IN_PROGRESS`: The file is currently being imported.
- `IMPORTED`: The file was successfully imported.
- `FAILED_TO_IMPORT`: The file failed to import.

**Example of `import_tracking.txt`:**

```
/path/to/db_export/record_file.csv.gz IMPORTED
/path/to/db_export/transaction/transaction_part_1.csv.gz IMPORTED
/path/to/db_export/transaction/transaction_part_2.csv.gz FAILED_TO_IMPORT
/path/to/db_export/account.csv.gz NOT_STARTED
```

### Steps to Handle Failed Imports:

1. **Identify Files to Re-import:**

   - Open the `import_tracking.txt` file.
   - Look for files with the status `FAILED_TO_IMPORT` or `NOT_STARTED`.
   - These files either failed to import or were not processed due to interruption.

2. **Re-run the Import Script:**

   - You can re-run the import script; it will skip files marked as `IMPORTED` and attempt to import files with statuses `NOT_STARTED`, `IN_PROGRESS`, or `FAILED_TO_IMPORT`.

     ```bash
     ./bootstrap.sh 8 /path/to/db_export/
     ```

   - The script will resume importing where it left off.

3. **Alternatively, Collect Specific Files to Re-import:**

   - Create a new directory to hold the files to be re-imported:

     ```bash
     mkdir -p /path/to/reimport_files
     ```

   - Copy the failed and not started files to the new directory:

     ```bash
     grep -E "FAILED_TO_IMPORT|NOT_STARTED" import_tracking.txt | awk '{print $1}' | xargs -I {} cp "{}" /path/to/reimport_files/
     ```

   - Run the import script, pointing it to the new directory:

     ```bash
     ./bootstrap.sh 8 /path/to/reimport_files/
     ```

4. **Verify the Imports:**

   - Check the `import_tracking.txt` and `import.log` files to ensure that all files have been imported successfully.
   - If files continue to fail, review the error messages in `import.log` for troubleshooting.

**Note on Data Consistency:**

- When a file import fails, the database transaction ensures that **no partial data** is committed.
- This means that when you re-run the import script, you can safely re-import failed files without worrying about duplicates or inconsistencies.
- The database tables remain in the same state as before the failed import attempt.

---

## Additional Notes

- **Data Integrity:** The import script ensures data integrity by using transactions. If an error occurs during the import of a file, that file's data will not be committed to the database.
- **System Resources:** Adjust the number of CPU cores used (`8` in the example) based on your system's capabilities to prevent overloading the server.
- **Security:** Ensure that the passwords set in the environment variables are kept secure and not exposed in logs or command history.
- **Concurrent Write Safety:** The script uses file locking (`flock`) to safely handle concurrent writes to `import_tracking.txt`. This prevents race conditions and ensures the tracking file remains consistent.
- **Resuming Imports:** The script maintains the status of all files in `import_tracking.txt`, allowing you to resume imports after an interruption without re-importing already imported files.
- **Required Tools:** Ensure that all required tools (`psql`, `gunzip`, `realpath`, `flock`) are installed on your system.

---

## Troubleshooting

- **Connection Errors:**

  - Confirm that `PGHOST` is correctly set to the IP address or hostname of your database server.
  - Ensure that the database server allows connections from your client machine.

- **Import Failures:**

  - Check the `import.log` file generated by the import script for detailed error messages.
  - Review the `import_tracking.txt` file to identify which files failed to import.

- **Interruption Handling:**

  - If the import process is interrupted (e.g., due to a network issue or manual cancellation), the script updates the statuses in `import_tracking.txt` accordingly.
    - Files that were in progress will be marked as `IN_PROGRESS` or remain as `NOT_STARTED` if they had not begun.
  - Upon restarting the script, it will:
    - Skip files marked as `IMPORTED`.
    - Attempt to import files with statuses `NOT_STARTED`, `IN_PROGRESS`, or `FAILED_TO_IMPORT`.

- **Bash Version Compatibility:**

  - The import script requires Bash version 4.3 or higher. Check your Bash version with:

    ```bash
    bash --version
    ```

  - If using an older version of Bash, consider updating to the minimum required version.

---
