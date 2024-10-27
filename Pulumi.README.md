# Next-Generation Infrastructure as Code with Pulumi and Java
​
A complete infrastructure for a microservice application in the Google Cloud Platform (GCP) as an example. To operate the microservice, we need an artifact registry to store the Docker image, a Kubernetes cluster to execute it, and a PostgreSQL database to store data. Finally, the microservice will be deployed to Kubernetes. Built using Pulumi and Java.

- **GCP Storage Bucket**: ${outputs.bucketUrl}
- **Docker Artifact Registry**: ${outputs.repositoryId}
- **GKE Cluster ${outputs.kubernetesClusterName}**: https://${outputs.kubernetesClusterEndpoint}
- **PostgreSQL Connection String**: ${outputs.postgresConnectionString}

- **Kubernetes Namespace**: ${outputs.namespace}
    - **Kubernetes Deployment**: ${outputs.deployment}
    - **Kubernetes Service**: ${outputs.service}