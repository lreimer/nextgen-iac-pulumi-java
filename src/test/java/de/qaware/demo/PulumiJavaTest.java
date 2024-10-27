package de.qaware.demo;

import com.pulumi.test.Mocks;
import com.pulumi.test.PulumiTest;
import com.pulumi.gcp.storage.Bucket;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static com.pulumi.test.PulumiTest.extractValue;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

class PulumiJavaTest {

    @AfterAll
    static void cleanup() {
        PulumiTest.cleanup();
    }

    @Test
    void testSetupStorageBucket() {
        // run the Pulumi program with mocks
        var result = PulumiTest.withMocks(new PulumiJavaMocks())
                .runTest(PulumiJava::setupStorageBucket);

        // get bucket resource and assert the results
        var bucket = result.resources().stream()
                .filter(r -> r instanceof Bucket).map(r -> (Bucket) r)
                .findFirst();
        assertThat(bucket).isPresent().hasValueSatisfying(b -> {
            assertThat(extractValue(b.location())).isEqualTo("EU");
        });
    }

    public static class PulumiJavaMocks implements Mocks {
        @Override
        public CompletableFuture<ResourceResult> newResourceAsync(ResourceArgs args) {
            return CompletableFuture.completedFuture(
                    ResourceResult.of(Optional.of(args.name), args.inputs));
        }
    }
}
