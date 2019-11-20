/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cli;

import static com.facebook.buck.util.environment.Platform.WINDOWS;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.apple.AppleNativeIntegrationTestUtils;
import com.facebook.buck.apple.toolchain.ApplePlatform;
import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.rules.knowntypes.KnownNativeRuleTypes;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.io.windowsfs.WindowsFS;
import com.facebook.buck.log.thrift.rulekeys.FullRuleKey;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.ThriftRuleKeyDeserializer;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.thrift.TException;
import org.hamcrest.Matchers;
import org.hamcrest.junit.MatcherAssert;
import org.immutables.value.Value;
import org.junit.Rule;
import org.junit.Test;

public class BuildCommandIntegrationTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();
  @Rule public TemporaryPaths tmp2 = new TemporaryPaths();

  @Test
  public void justBuild() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();
    workspace.runBuckBuild("--just-build", "//:bar", "//:foo", "//:ex ample").assertSuccess();
    assertThat(
        workspace.getBuildLog().getAllTargets(),
        Matchers.contains(BuildTargetFactory.newInstance("//:bar")));
  }

  @Test
  public void showOutput() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();
    ProcessResult runBuckResult = workspace.runBuckBuild("--show-output", "//:bar");
    runBuckResult.assertSuccess();
    assertThat(runBuckResult.getStdout(), Matchers.containsString("//:bar buck-out"));
  }

  @Test
  public void showFullOutput() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();
    ProcessResult runBuckResult = workspace.runBuckBuild("--show-full-output", "//:bar");
    runBuckResult.assertSuccess();
    Path expectedRootDirectory = tmp.getRoot();
    String expectedOutputDirectory = expectedRootDirectory.resolve("buck-out/").toString();
    String stdout = runBuckResult.getStdout();
    assertThat(stdout, Matchers.containsString("//:bar "));
    assertThat(stdout, Matchers.containsString(expectedOutputDirectory));
  }

  @Test
  public void showJsonOutput() throws IOException {
    assumeThat(Platform.detect(), is(not(WINDOWS)));
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();
    ProcessResult runBuckResult =
        workspace.runBuckBuild("--show-json-output", "//:foo", "//:bar", "//:ex ample");
    runBuckResult.assertSuccess();
    assertThat(
        runBuckResult.getStdout(),
        Matchers.containsString(
            "\"//:bar\" : \"buck-out/gen/bar/bar\",\n  \"//:ex ample\" : \"buck-out/gen/ex ample/example\",\n  \"//:foo\" : \"buck-out/gen/foo/foo\"\n}"));
  }

  @Test
  public void showFullJsonOutput() throws IOException {
    assumeThat(Platform.detect(), is(not(WINDOWS)));
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build/sub folder", tmp);
    workspace.setUp();
    ProcessResult runBuckResult =
        workspace.runBuckBuild("--show-full-json-output", "//:bar", "//:foo", "//:ex ample");
    runBuckResult.assertSuccess();
    Path expectedRootDirectory = tmp.getRoot();
    String expectedOutputDirectory = expectedRootDirectory.resolve("buck-out/").toString();
    assertThat(
        runBuckResult.getStdout(),
        Matchers.containsString(
            "{\n  \"//:bar\" : \""
                + expectedOutputDirectory
                + "/gen/bar/bar\",\n  \"//:ex ample\" : \""
                + expectedOutputDirectory
                + "/gen/ex ample/example\",\n  \"//:foo\" : \""
                + expectedOutputDirectory
                + "/gen/foo/foo\"\n}"));
  }

  @Test
  public void showRuleKey() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();
    ProcessResult runBuckResult = workspace.runBuckBuild("--show-rulekey", "//:bar");
    runBuckResult.assertSuccess();

    Pattern pattern = Pattern.compile("\\b[0-9a-f]{5,40}\\b"); // sha
    Matcher shaMatcher = pattern.matcher(runBuckResult.getStdout());
    assertThat(shaMatcher.find(), Matchers.equalTo(true));
    String shaValue = shaMatcher.group();
    assertThat(shaValue.length(), Matchers.equalTo(40));
    assertThat(runBuckResult.getStdout(), Matchers.containsString("//:bar " + shaValue));
  }

  @Test
  public void showRuleKeyAndOutput() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();
    ProcessResult runBuckResult =
        workspace.runBuckBuild("--show-output", "--show-rulekey", "//:bar");
    runBuckResult.assertSuccess();

    Pattern pattern = Pattern.compile("\\b[0-9a-f]{5,40}\\b"); // sha
    Matcher shaMatcher = pattern.matcher(runBuckResult.getStdout());
    assertThat(shaMatcher.find(), Matchers.equalTo(true));
    String shaValue = shaMatcher.group();
    assertThat(shaValue.length(), Matchers.equalTo(40));
    assertThat(
        runBuckResult.getStdout(), Matchers.containsString("//:bar " + shaValue + " buck-out"));
  }

  @Test
  public void buckBuildAndCopyOutputFileWithBuildTargetThatSupportsIt() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "build_into", tmp);
    workspace.setUp();

    Path externalOutputs = tmp.newFolder("into-output");
    Path output = externalOutputs.resolve("the_example.jar");
    assertFalse(output.toFile().exists());
    workspace.runBuckBuild("//:example", "--out", output.toString()).assertSuccess();
    assertTrue(output.toFile().exists());

    ZipInspector zipInspector = new ZipInspector(output);
    zipInspector.assertFileExists("com/example/Example.class");
  }

  @Test
  public void buckBuildAndCopyOutputFileIntoDirectoryWithBuildTargetThatSupportsIt()
      throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "build_into", tmp);
    workspace.setUp();

    Path outputDir = tmp.newFolder("into-output");
    assertEquals(0, outputDir.toFile().listFiles().length);
    workspace.runBuckBuild("//:example", "--out", outputDir.toString());
    assertTrue(outputDir.toFile().isDirectory());
    File[] files = outputDir.toFile().listFiles();
    assertEquals(1, files.length);
    assertTrue(Files.isRegularFile(outputDir.resolve("example.jar")));
  }

  @Test
  public void buckBuildAndCopyOutputFileWithBuildTargetThatDoesNotSupportIt() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "build_into", tmp);
    workspace.setUp();

    Path externalOutputs = tmp.newFolder("into-output");
    Path output = externalOutputs.resolve("pylib.zip");
    assertFalse(output.toFile().exists());
    ProcessResult result = workspace.runBuckBuild("//:example_py", "--out", output.toString());
    result.assertFailure();
    assertThat(
        result.getStderr(),
        Matchers.containsString(
            "//:example_py does not have an output that is compatible with `buck build --out`"));
  }

  @Test
  public void lastOutputDir() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();
    ProcessResult runBuckResult =
        workspace.runBuckBuild("-c", "build.create_build_output_symlinks_enabled=true", "//:bar");
    runBuckResult.assertSuccess();
    assertTrue(
        Files.exists(workspace.getBuckPaths().getLastOutputDir().toAbsolutePath().resolve("bar")));
  }

  @Test
  public void lastOutputDirForAppleBundle() throws IOException {
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "apple_app_bundle", tmp);
    workspace.setUp();
    ProcessResult runBuckResult =
        workspace.runBuckBuild(
            "-c", "build.create_build_output_symlinks_enabled=true", "//:DemoApp#dwarf-and-dsym");
    runBuckResult.assertSuccess();
    assertTrue(
        Files.exists(
            workspace.getBuckPaths().getLastOutputDir().toAbsolutePath().resolve("DemoApp.app")));
    assertTrue(
        Files.exists(
            workspace
                .getBuckPaths()
                .getLastOutputDir()
                .toAbsolutePath()
                .resolve("DemoAppBinary#apple-dsym,iphonesimulator-x86_64.dSYM")));
  }

  @Test
  public void writesBinaryRuleKeysToDisk() throws IOException, TException {
    Path logFile = tmp.newFile("out.bin");
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();
    ProcessResult runBuckResult =
        workspace.runBuckBuild(
            "--show-rulekey", "--rulekeys-log-path", logFile.toAbsolutePath().toString(), "//:bar");
    runBuckResult.assertSuccess();

    List<FullRuleKey> ruleKeys = ThriftRuleKeyDeserializer.readRuleKeys(logFile);
    // Three rules, they could have any number of sub-rule keys and contributors
    assertTrue(ruleKeys.size() >= 3);
    assertTrue(ruleKeys.stream().anyMatch(ruleKey -> ruleKey.name.equals("//:bar")));
  }

  @Test
  public void configuredBuckoutSymlinkinSubdirWorksWithoutCells() throws IOException {
    assumeFalse(Platform.detect() == WINDOWS);

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();
    ProcessResult runBuckResult =
        workspace.runBuckBuild(
            "-c",
            "project.buck_out_compat_link=true",
            "-c",
            "project.buck_out=buck-out/mydir",
            "//:foo",
            "//:bar",
            "//:ex ample");
    runBuckResult.assertSuccess();

    assertTrue(Files.exists(workspace.getPath("buck-out/mydir/bin")));
    assertTrue(Files.exists(workspace.getPath("buck-out/mydir/gen")));

    Path buckOut = workspace.resolve("buck-out");
    assertEquals(
        buckOut.resolve("mydir/bin"),
        buckOut.resolve(Files.readSymbolicLink(buckOut.resolve("bin"))));
    assertEquals(
        buckOut.resolve("mydir/gen"),
        buckOut.resolve(Files.readSymbolicLink(buckOut.resolve("gen"))));
  }

  @Test
  public void enableEmbeddedCellHasOnlyOneBuckOut() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "multiple_cell_build", tmp);
    workspace.setUp();
    ProcessResult runBuckResult =
        workspace.runBuckBuild("-c", "project.embedded_cell_buck_out_enabled=true", "//main/...");
    runBuckResult.assertSuccess();

    assertTrue(Files.exists(workspace.getPath("buck-out/cells/cxx")));
    assertTrue(Files.exists(workspace.getPath("buck-out/cells/java")));

    assertFalse(Files.exists(workspace.getPath("cxx/buck-out")));
    assertFalse(Files.exists(workspace.getPath("java/buck-out")));
  }

  @Test
  public void testFailsIfNoTargetsProvided() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("build");
    result.assertExitCode(null, ExitCode.COMMANDLINE_ERROR);
    MatcherAssert.assertThat(
        result.getStderr(),
        Matchers.containsString(
            "Must specify at least one build target. See https://buck.build/concept/build_target_pattern.html"));
  }

  @Test
  public void handlesRelativeTargets() throws Exception {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();

    Path absolutePath = workspace.buildAndReturnOutput("//subdir1/subdir2:bar");

    workspace.setRelativeWorkingDirectory(Paths.get("subdir1"));

    Path subdirRelativePath = workspace.buildAndReturnOutput("subdir2:bar");

    Path subdirAbsolutePath = workspace.buildAndReturnOutput("//subdir1/subdir2:bar");

    assertEquals(absolutePath, subdirAbsolutePath);
    assertEquals(absolutePath, subdirRelativePath);
  }

  @Test
  public void canBuildAndUseRelativePathsFromWithinASymlinkedDirectory() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();

    assertFalse(tmp.getRoot().startsWith(tmp2.getRoot()));
    assertFalse(tmp2.getRoot().startsWith(tmp.getRoot()));

    Path dest = tmp2.getRoot().resolve("symlink_subdir").toAbsolutePath();
    Path relativeDest = tmp.getRoot().relativize(dest);

    MorePaths.createSymLink(new WindowsFS(), dest, tmp.getRoot());

    workspace.setRelativeWorkingDirectory(relativeDest);

    Path absolutePath = workspace.buildAndReturnOutput("//subdir1/subdir2:bar");

    workspace.setRelativeWorkingDirectory(relativeDest.resolve("subdir1"));

    Path subdirAbsolutePath = workspace.buildAndReturnOutput("//subdir1/subdir2:bar");
    Path subdirRelativePath = workspace.buildAndReturnOutput("subdir2:bar");

    assertEquals(absolutePath, subdirAbsolutePath);
    assertEquals(absolutePath, subdirRelativePath);
  }

  @Test
  public void printsAFriendlyErrorWhenRelativePathDoesNotExistInPwdButDoesInRoot()
      throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "just_build", tmp);
    workspace.setUp();

    String expectedWhenExists =
        "%s references a non-existent directory subdir1/subdir3 when run from subdir1";
    String expectedWhenNotExists = "%s references non-existent directory subdir1/subdir4";

    workspace.setRelativeWorkingDirectory(Paths.get("subdir1"));
    Files.createDirectories(tmp.getRoot().resolve("subdir3").resolve("something"));

    String recursiveTarget = workspace.runBuckBuild("subdir3/...").assertFailure().getStderr();
    String packageTarget = workspace.runBuckBuild("subdir3:").assertFailure().getStderr();
    String specificTarget = workspace.runBuckBuild("subdir3:target").assertFailure().getStderr();
    String noRootDirRecursiveTarget =
        workspace.runBuckBuild("subdir4/...").assertFailure().getStderr();
    String noRootDirPackageTarget = workspace.runBuckBuild("subdir4:").assertFailure().getStderr();
    String noRootDirSpecificTarget =
        workspace.runBuckBuild("subdir4:target").assertFailure().getStderr();

    assertThat(
        recursiveTarget, Matchers.containsString(String.format(expectedWhenExists, "subdir3/...")));
    assertThat(
        packageTarget, Matchers.containsString(String.format(expectedWhenExists, "subdir3:")));
    assertThat(
        specificTarget,
        Matchers.containsString(String.format(expectedWhenExists, "subdir3:target")));

    assertThat(
        noRootDirRecursiveTarget,
        Matchers.containsString(String.format(expectedWhenNotExists, "subdir4/...")));
    assertThat(
        noRootDirPackageTarget,
        Matchers.containsString(String.format(expectedWhenNotExists, "subdir4:")));
    assertThat(
        noRootDirSpecificTarget,
        Matchers.containsString(String.format(expectedWhenNotExists, "subdir4:target")));
  }

  @BuckStyleImmutable
  @Value.Immutable
  abstract static class AbstractThrowInConstructorArg implements BuildRuleArg {}

  private static class ThrowInConstructor
      implements DescriptionWithTargetGraph<ThrowInConstructorArg> {

    @Override
    public Class<ThrowInConstructorArg> getConstructorArgType() {
      return ThrowInConstructorArg.class;
    }

    @Override
    public BuildRule createBuildRule(
        BuildRuleCreationContextWithTargetGraph context,
        BuildTarget buildTarget,
        BuildRuleParams params,
        ThrowInConstructorArg args) {
      throw new HumanReadableException("test test test");
    }
  }

  @Test
  public void ruleCreationError() throws Exception {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "rule_creation_error", tmp);
    workspace.setUp();
    workspace.setKnownRuleTypesFactoryFactory(
        (executor,
            pluginManager,
            sandboxExecutionStrategyFactory,
            knownConfigurationDescriptions) ->
            cell ->
                KnownNativeRuleTypes.of(
                    ImmutableList.of(new ThrowInConstructor()), knownConfigurationDescriptions));
    ProcessResult result = workspace.runBuckBuild(":qq");
    result.assertFailure();
    MatcherAssert.assertThat(result.getStderr(), Matchers.containsString("test test test"));
    MatcherAssert.assertThat(
        result.getStderr(), Matchers.not(Matchers.containsString("Exception")));
  }
}
