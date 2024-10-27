package de.qaware.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import com.pulumi.Context;
import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.pulumi.dockerbuild.Image;
import com.pulumi.dockerbuild.ImageArgs;
import com.pulumi.dockerbuild.enums.Platform;
import com.pulumi.dockerbuild.inputs.BuildContextArgs;
import com.pulumi.exceptions.RunException;
import com.pulumi.gcp.artifactregistry.Repository;
import com.pulumi.gcp.artifactregistry.RepositoryArgs;
import com.pulumi.gcp.artifactregistry.inputs.RepositoryDockerConfigArgs;
import com.pulumi.gcp.container.Cluster;
import com.pulumi.gcp.container.ClusterArgs;
import com.pulumi.gcp.container.inputs.ClusterAddonsConfigArgs;
import com.pulumi.gcp.container.inputs.ClusterAddonsConfigHorizontalPodAutoscalingArgs;
import com.pulumi.gcp.container.inputs.ClusterAddonsConfigHttpLoadBalancingArgs;
import com.pulumi.gcp.container.inputs.ClusterNodePoolArgs;
import com.pulumi.gcp.container.inputs.ClusterNodePoolAutoscalingArgs;
import com.pulumi.gcp.container.inputs.ClusterNodePoolNodeConfigArgs;
import com.pulumi.gcp.sql.DatabaseInstance;
import com.pulumi.gcp.sql.DatabaseInstanceArgs;
import com.pulumi.gcp.sql.inputs.DatabaseInstanceSettingsArgs;
import com.pulumi.gcp.storage.Bucket;
import com.pulumi.gcp.storage.BucketArgs;
import com.pulumi.gcp.storage.inputs.BucketCorArgs;
import com.pulumi.kubernetes.Provider;
import com.pulumi.kubernetes.ProviderArgs;
import com.pulumi.kubernetes.apps.v1.Deployment;
import com.pulumi.kubernetes.apps.v1.DeploymentArgs;
import com.pulumi.kubernetes.apps.v1.inputs.DeploymentSpecArgs;
import com.pulumi.kubernetes.core.v1.Namespace;
import com.pulumi.kubernetes.core.v1.NamespaceArgs;
import com.pulumi.kubernetes.core.v1.Service;
import com.pulumi.kubernetes.core.v1.ServiceArgs;
import com.pulumi.kubernetes.core.v1.inputs.ContainerArgs;
import com.pulumi.kubernetes.core.v1.inputs.ContainerPortArgs;
import com.pulumi.kubernetes.core.v1.inputs.EnvVarArgs;
import com.pulumi.kubernetes.core.v1.inputs.PodSpecArgs;
import com.pulumi.kubernetes.core.v1.inputs.PodTemplateSpecArgs;
import com.pulumi.kubernetes.core.v1.inputs.ServicePortArgs;
import com.pulumi.kubernetes.core.v1.inputs.ServiceSpecArgs;
import com.pulumi.kubernetes.meta.v1.inputs.LabelSelectorArgs;
import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs;
import com.pulumi.resources.CustomResourceOptions;

/**
 * A complete infrastructure for a microservice application in the Google Cloud
 * Platform (GCP).
 * 
 * To operate the microservice, we need
 * <ul>
 * <li>an artifact registry to store the Docker image,</li>
 * <li>a Kubernetes cluster to execute it, and</li>
 * <li>a PostgreSQL database to store data.</li>
 * 
 * Finally, the microservice will be deployed to Kubernetes. Built using Pulumi
 * and Java.
 */
public class PulumiJava {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            setupStorageBucket(ctx);

            setupDockerRepository(ctx);
            // buildDockerImage(ctx);

            var cluster = setupAutopilotKubernetesCluster(ctx);
            // var cluster = setupManagedKubernetesCluster(ctx);

            var database = setupPostgresDatabase(ctx);

            deployMicroservice(ctx, cluster, database);

            readAndExportReadme(ctx);
        });
    }

   static void setupStorageBucket(Context ctx) {
        // Create a GCP resource (Storage Bucket)
        var bucket = new Bucket("java-pulumi-bucket", BucketArgs.builder()
                .location("EU")
                .cors(BucketCorArgs.builder()
                        .maxAgeSeconds(3600)
                        .methods("GET", "HEAD")
                        .origins("*")
                        .responseHeaders("Content-Type")
                        .build())
                .forceDestroy(true)
                .build());

        ctx.export("bucketUrl", bucket.url());
    }

    static void setupDockerRepository(Context ctx) {
        // read the region from the Pulumi configuration
        var location = retrieveRegion(ctx);

        // Create a GCP Artifact Registry repository for Docker images
        var repository = new Repository("microservice-repo",
                RepositoryArgs.builder()
                        .repositoryId("microservice-repo")
                        // Updated location to Europe (Belgium)
                        .location(location)
                        // Docker image repository
                        .format("Docker")
                        .description("Docker repository for mciroservice")
                        .dockerConfig(RepositoryDockerConfigArgs.builder()
                                // Immutable tags enabled
                                .immutableTags(true)
                                .build())
                        .build());

        // Export the repository ID
        ctx.export("repositoryId", repository.id());
    }

    static void buildDockerImage(Context ctx) {
        // build the Docker image
        var image = new Image("microservice-image", ImageArgs.builder()
                .tags("ghcr.io/lreimer/" + ctx.projectName() + ":main")
                .context(BuildContextArgs.builder()
                        .location("./src/main/docker")
                        .build())
                .platforms(Platform.Linux_amd64, Platform.Linux_arm64)
                .buildOnPreview(true)
                .push(false)
                .build());

        ctx.export("imageRef", image.ref());
    }

    static Cluster setupAutopilotKubernetesCluster(Context ctx) {
        var region = retrieveRegion(ctx);

        // create a GKE autopilot cluster
        var cluster = new Cluster("pulumi-java-auto-cluster", ClusterArgs.builder()
                .deletionProtection(false)
                .location(region)
                .enableAutopilot(true)
                // we could also use semantic versioning here, like 1.31
                // .nodeVersion("latest")
                // .minMasterVersion("latest")
                .build());

        // export the cluster endpoint and name
        ctx.export("kubernetesClusterEndpoint", cluster.endpoint());
        ctx.export("kubernetesClusterName", cluster.name());
        ctx.export("kubeconfig", Output.of(generateKubeconfig(cluster)));

        return cluster;
    }

    static Cluster setupManagedKubernetesCluster(Context ctx) {
        var region = retrieveRegion(ctx);

        var cluster = new Cluster("pulumi-java-regional-cluster", ClusterArgs.builder()
                .name("pulumi-java-regional-cluster")
                .deletionProtection(false)
                .location(region)
                .nodeVersion("1.31")
                .minMasterVersion("1.31")
                .nodePools(ClusterNodePoolArgs.builder()
                        .name("default-pool")
                        .initialNodeCount(1)
                        .nodeConfig(ClusterNodePoolNodeConfigArgs.builder()
                                .machineType("n2-standard-8")
                                .build())
                        .autoscaling(ClusterNodePoolAutoscalingArgs.builder()
                                .minNodeCount(1)
                                .maxNodeCount(3)
                                .build())
                        .build())
                .addonsConfig(ClusterAddonsConfigArgs.builder()
                        .horizontalPodAutoscaling(ClusterAddonsConfigHorizontalPodAutoscalingArgs.builder()
                                .disabled(false)
                                .build())
                        .httpLoadBalancing(ClusterAddonsConfigHttpLoadBalancingArgs.builder()
                                .disabled(false)
                                .build())
                        .build())
                .build());

        // export the cluster endpoint and name
        ctx.export("kubernetesClusterEndpoint", cluster.endpoint());
        ctx.export("kubernetesClusterName", cluster.name());
        ctx.export("kubeconfig", generateKubeconfig(cluster));

        return cluster;
    }

    private static Output<String> generateKubeconfig(Cluster cluster) {
        return Output.format(
        """
        apiVersion: v1
        kind: Config                
        clusters:
        - name: %1$s
          cluster:
            certificate-authority-data: %2$s
            server: https://%3$s
        contexts:
        - name: %1$s
          context:
            cluster: %1$s
            user: %1$s
        current-context: %1$s
        users:
        - name: %1$s
          user:
            exec:
              apiVersion: client.authentication.k8s.io/v1beta1
              command: gke-gcloud-auth-plugin
              installHint: Install gke-gcloud-auth-plugin for use with kubectl
              interactiveMode: IfAvailable
              provideClusterInfo: true
        """,
        cluster.name().applyValue(name -> name),
        cluster.masterAuth().applyValue(auth -> auth.clusterCaCertificate().orElse("")),
        cluster.endpoint().applyValue(endpoint -> endpoint));
    }

    static DatabaseInstance setupPostgresDatabase(Context ctx) {
        var region = retrieveRegion(ctx);

        var postgresInstance = new DatabaseInstance("pulumi-java-postgres-db", 
            DatabaseInstanceArgs.builder()
                .region(region)
                .databaseVersion("POSTGRES_14")
                .settings(DatabaseInstanceSettingsArgs.builder()
                        .tier("db-f1-micro")
                        .build())
                .build());

        ctx.export("postgresConnectionString", postgresInstance.connectionName());

        return postgresInstance;
    }

    static void deployMicroservice(Context ctx, Cluster cluster, DatabaseInstance database) {
        // var kubeconfig = generateKubeconfig(cluster);
        String kubeconfig;
        try {
           kubeconfig = Files.readString(Paths.get("./kubeconfig"));
        } catch (IOException e) {
           throw new RuntimeException(e);
        }

        var provider = new Provider("gke-provider", ProviderArgs.builder()
                .kubeconfig(kubeconfig)
                .build());

        // create the microservice Kubernetes resources
        var appLabels = Map.of("app", "microservice");
        var options = CustomResourceOptions.builder().provider(provider).build();
        var namespace = new Namespace("microservice", NamespaceArgs.builder()
            .metadata(ObjectMetaArgs.builder()
                .name("microservice")
                .build())
            .build(), options);
        
        var deployment = new Deployment("microservice-deployment", DeploymentArgs.builder()
                .metadata(ObjectMetaArgs.builder()
                    .namespace(namespace.metadata().applyValue(meta -> meta.name().get()))
                    .build())
                .spec(DeploymentSpecArgs.builder()
                    .replicas(2)
                    .selector(LabelSelectorArgs.builder().matchLabels(appLabels).build())
                    .template(PodTemplateSpecArgs.builder()
                        .metadata(ObjectMetaArgs.builder().labels(appLabels).build())
                        .spec(PodSpecArgs.builder()
                            .containers(ContainerArgs.builder()
                                .name("microservice")
                                .image("ghcr.io/lreimer/nextgen-iac-pulumi-java:main")
                                .ports(ContainerPortArgs.builder()
                                    .name("http")
                                    .containerPort(10000)
                                    .build())
                                .env(EnvVarArgs.builder()
                                    .name("DATABASE_URL")
                                    .value(database.connectionName())
                                    .build())
                                .build())
                            .build())
                        .build())
                    .build())
                .build(), options);

        var service = new Service("microservice-service", ServiceArgs.builder()
                .metadata(ObjectMetaArgs.builder()
                    .namespace(namespace.metadata().applyValue(meta -> meta.name().get()))
                    .build())
                .spec(ServiceSpecArgs.builder()
                    .selector(appLabels)
                    .ports(ServicePortArgs.builder()
                        .port(80)
                        .targetPort("http")
                        .build())
                    .type("LoadBalancer")
                    .build())
                .build());
        
        ctx.export("namespace", namespace.metadata().applyValue(meta -> meta.name()));
        ctx.export("deployment", deployment.metadata().applyValue(meta -> meta.name()));
        ctx.export("service", service.metadata().applyValue(meta -> meta.name()));
    }

    private static String retrieveRegion(Context ctx) {
        return ctx.config().get("gcp:region").orElse("europe-west1");
    }

    static void readAndExportReadme(Context ctx) {
        try {
            // read the Pulumi README file and export it as an output
            var readme = Files.readString(Paths.get("./Pulumi.README.md"));
            ctx.export("readme", Output.of(readme));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
