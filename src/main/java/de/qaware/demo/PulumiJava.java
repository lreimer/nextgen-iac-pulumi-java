package de.qaware.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.pulumi.Context;
import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.pulumi.dockerbuild.Image;
import com.pulumi.dockerbuild.ImageArgs;
import com.pulumi.dockerbuild.enums.Platform;
import com.pulumi.dockerbuild.inputs.BuildContextArgs;
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
import com.pulumi.gcp.storage.Bucket;
import com.pulumi.gcp.storage.BucketArgs;
import com.pulumi.gcp.storage.inputs.BucketCorArgs;

/** 
 * A complete infrastructure for a microservice application in the Google Cloud Platform (GCP). 
 * 
 * To operate the microservice, we need 
 * <ul>
 * <li>an artifact registry to store the Docker image,</li>
 * <li>a Kubernetes cluster to execute it, and</li>
 * <li>a PostgreSQL database to store data.</li>
 * 
 * Finally, the microservice will be deployed to Kubernetes. Built using Pulumi and Java.
 */
public class PulumiJava {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            setupStorageBucket(ctx);   
            setupDockerRepository(ctx);
            // buildDockerImage(ctx);
            setupAutopilotKubernetesCluster(ctx);
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
                .tags("gcr.io/lreimer/" + ctx.projectName() + "microservice:latest")
                .context(BuildContextArgs.builder()
                        .location("./src/main/docker")
                        .build())
                .platforms(Platform.Linux_amd64, Platform.Linux_arm64)
                .buildOnPreview(true)                
                .push(false)
                .build());

        ctx.export("imageRef", image.ref());
    }

    static void setupAutopilotKubernetesCluster(Context ctx) {
        var region = retrieveRegion(ctx);

        // create a GKE autopilot cluster
        var cluster = new Cluster("pulumi-java-auto-cluster", ClusterArgs.builder()
            .deletionProtection(false)
            .location(region)
            .enableAutopilot(true)
            // we could also use semantic versioning here, like 1.30
            .nodeVersion("latest")
            .minMasterVersion("latest")
            .build());

        // export the cluster endpoint and name
        ctx.export("kubernetesClusterEndpoint", cluster.endpoint());
        ctx.export("kubernetesClusterName", cluster.name());
    }

    static void setupRegionalKubernetesCluster(Context ctx) {
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
    }

    static void setupPostgresDatabase(Context ctx) {
        
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
