# Database

## Application Status

The Mirror Node saves its current state to the database in a table called `t_application_status`.
While the values in this table are updated in real time, any changes you wish to make here to promote to the application require that you first stop the application, make the changes in the database, then restart the application

| Status name  | Description  |
|---|---|
| Last processed record hash | The hash of the last record file processed into the database |
| Last processed event hash | The hash of the last event file processed into the database |
| Last valid downloaded record file name | The name of the last record file to have passed signature verification |
| Last valid downloaded record file hash | The hash of the last record file to have passed signature verification |
| Last valid downloaded event file name | The name of the last event file to have passed signature verification |
| Last valid downloaded event file hash | The hash of the last event file to have passed signature verification |
| Last valid downloaded balance file name | The name of the last balance file to have passed signature verification |
