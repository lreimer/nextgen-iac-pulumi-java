# Next-Generation Infrastructure as Code with Pulumi and Java

Repository for JAVASpektrum article on next generation Infrastructure as Code using Pulumi and Java.

In this repository, we will build a complete infrastructure for a microservice application in the Google Cloud Platform (GCP) as an example. To operate the application, we need an artifact registry to store the Docker image, a Kubernetes cluster to execute it, and a PostgreSQL database to store data. Finally, the microservice will be deployed to Kubernetes. Built entirely using Pulumi and Java.

```bash
# to create the entire infrastructure
pulumi up

# to destroy the entire infrastructure
pulumi destroy
```

## Maintainer

M.-Leander Reimer (@lreimer), <mario-leander.reimer@qaware.de>

## License

This software is provided under the MIT open source license, read the `LICENSE` file for details.
