package de.qaware.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.pulumi.Context;
import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.pulumi.gcp.artifactregistry.Repository;
import com.pulumi.gcp.artifactregistry.RepositoryArgs;
import com.pulumi.gcp.artifactregistry.inputs.RepositoryDockerConfigArgs;
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
        var location = ctx.config().get("gcp:region").orElse("europe-west1");

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

    static void readAndExportReadme(Context ctx) {
        try {
            // Read the README file and export it as an output
            var readme = Files.readString(Paths.get("./Pulumi.README.md"));
            ctx.export("readme", Output.of(readme));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
