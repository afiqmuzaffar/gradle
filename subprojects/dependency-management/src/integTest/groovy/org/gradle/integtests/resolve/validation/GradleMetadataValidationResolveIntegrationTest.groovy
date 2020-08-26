/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.resolve.validation

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.test.fixtures.gradle.GradleFileModuleAdapter
import org.gradle.test.fixtures.maven.MavenFileRepository
import spock.lang.Issue

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class GradleMetadataValidationResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can resolve if component gav information is missing"() {
        GradleFileModuleAdapter.printComponentGAV = false
        buildFile << """
            dependencies {
                conf 'org.test:projectA:1.1'
            }
        """

        when:
        repository {
            'org.test:projectA:1.1'()
        }
        repositoryInteractions {
            'org.test:projectA:1.1' {
                expectResolve()
            }
        }

        then:
        succeeds ":checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.test:projectA:1.1")
            }
        }

        cleanup:
        GradleFileModuleAdapter.printComponentGAV = true
    }

    @ToBeFixedForConfigurationCache
    def "fails with proper error if a mandatory attribute is not defined"() {
        buildFile << """
            dependencies {
                conf 'org.test:projectA:1.1'
            }
        """

        when:
        repository {
            'org.test:projectA:1.1' {
                variant("api") {
                    artifact("name", null)
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA:1.1' {
                expectGetMetadata()
            }
        }

        then:
        fails ":checkDeps"
        failure.assertHasCause("missing 'url' at /variants[0]/files[0]")
    }

    @Issue("gradle/gradle#7888")
    @ToBeFixedForConfigurationCache
    def "fails with reasonable error message if Gradle Module Metadata doesn't declare any variant"() {
        buildFile << """
            dependencies {
                conf 'org.test:projectA:1.1'
            }
        """

        when:
        repository {
            'org.test:projectA:1.1' {
                withModule {
                    withoutDefaultVariants()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA:1.1' {
                expectGetMetadata()
            }
        }

        then:
        fails ":checkDeps"
        failure.assertHasCause("Gradle Module Metadata for module org.test:projectA:1.1 is invalid because it doesn't declare any variant")
    }

    @UnsupportedWithConfigurationCache(because = "tries to revisit a file collection which failed to resolve")
    def "fails if an available-at module isn't published"() {
        buildFile << """
            dependencies {
                conf 'org.test:module:1.0'
            }
        """

        when:
        repository {
            'org.test:module:1.0' {
                variants(['api', 'runtime']) {
                    availableAt("../../module2/1.0/module2-1.0.module", "org.test", "module2", "1.0")
                }
            }
        }

        then:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
            }
            'org.test:module2:1.0' {
                withModule {
                    moduleMetadata.expectGetMissing()
                }
            }
        }
        fails ":checkDeps"
        failure.assertHasCause("Module org.test:module:1.0 is invalid because it references a variant from a module which isn't published in the same repository.")
    }

    @UnsupportedWithConfigurationCache(because = "tries to revisit a file collection which failed to resolve")
    def "fails if an available-at module is published in a different repository"() {
        def otherRepo = new MavenFileRepository(temporaryFolder.createDir("other-repo"))
        otherRepo.module('org.test', 'module2', '1.0').publish()

        buildFile << """
            repositories {
                maven {
                    name = 'other'
                    url = '${otherRepo.uri}'
                }
            }
            dependencies {
                conf 'org.test:module:1.0'
            }
        """

        when:
        repository {
            'org.test:module:1.0' {
                variants(['api', 'runtime']) {
                    availableAt("../../module2/1.0/module2-1.0.module", "org.test", "module2", "1.0")
                }
            }
        }

        then:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
            }
            'org.test:module2:1.0' {
                withModule {
                    moduleMetadata.expectGetMissing()
                }
            }
        }
        fails ":checkDeps"
        failure.assertHasCause("Module org.test:module:1.0 is invalid because it references a variant from a module which isn't published in the same repository.")
    }

}
