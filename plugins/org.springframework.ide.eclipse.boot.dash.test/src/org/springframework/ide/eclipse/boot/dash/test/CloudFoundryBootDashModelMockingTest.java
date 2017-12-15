/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.ide.eclipse.boot.dash.test.BootDashModelTest.waitForJobsToComplete;
import static org.springframework.ide.eclipse.boot.dash.test.CloudFoundryTestHarness.APP_DELETE_TIMEOUT;
import static org.springframework.ide.eclipse.boot.dash.test.CloudFoundryTestHarness.APP_DEPLOY_TIMEOUT;
import static org.springframework.ide.eclipse.boot.dash.test.util.JobUtil.runInJob;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.withPackaging;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.withStarters;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.assertContains;
import static org.springsource.ide.eclipse.commons.tests.util.StsTestCase.createFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.jface.action.IAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.core.internal.MavenSpringBootProject;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudAppDashElement;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudFoundryBootDashModel;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudFoundryRunTargetType;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudFoundryTargetWizardModel.LoginMethod;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.CFApplication;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.CFClientParams;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.CFCredentials;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.CFCredentials.CFCredentialType;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.CFDomainType;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.ClientRequests;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.HealthChecks;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.v2.CFDomainStatus;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.v2.ReactorUtils;
import org.springframework.ide.eclipse.boot.dash.dialogs.DeploymentPropertiesDialogModel.ManifestType;
import org.springframework.ide.eclipse.boot.dash.dialogs.EditTemplateDialogModel;
import org.springframework.ide.eclipse.boot.dash.dialogs.ManifestDiffDialogModel;
import org.springframework.ide.eclipse.boot.dash.dialogs.PasswordDialogModel;
import org.springframework.ide.eclipse.boot.dash.dialogs.StoreCredentialsMode;
import org.springframework.ide.eclipse.boot.dash.model.AbstractBootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ModelStateListener;
import org.springframework.ide.eclipse.boot.dash.model.LocalBootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.RefreshState;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.SecuredCredentialsStore;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.test.mocks.MockCFApplication;
import org.springframework.ide.eclipse.boot.dash.test.mocks.MockCFSpace;
import org.springframework.ide.eclipse.boot.dash.test.mocks.MockCloudFoundryClientFactory;
import org.springframework.ide.eclipse.boot.dash.test.mocks.RunStateHistory;
import org.springframework.ide.eclipse.boot.dash.test.util.ZipDiff;
import org.springframework.ide.eclipse.boot.dash.util.CancelationTokens;
import org.springframework.ide.eclipse.boot.dash.views.BootDashActions;
import org.springframework.ide.eclipse.boot.dash.views.CustmomizeTargetLabelAction;
import org.springframework.ide.eclipse.boot.dash.views.CustomizeTargetLabelDialogModel;
import org.springframework.ide.eclipse.boot.dash.views.UpdatePasswordAction;
import org.springframework.ide.eclipse.boot.pstore.IPropertyStore;
import org.springframework.ide.eclipse.boot.pstore.PropertyStoreApi;
import org.springframework.ide.eclipse.boot.test.AutobuildingEnablement;
import org.springframework.ide.eclipse.boot.test.BootProjectTestHarness;
import org.springframework.ide.eclipse.boot.test.util.TestBracketter;
import org.springframework.util.StringUtils;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ACondition;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

/**
 * @author Kris De Volder
 */
public class CloudFoundryBootDashModelMockingTest {

	private TestBootDashModelContext context;
	private BootProjectTestHarness projects;
	private UserInteractions ui;
	private MockCloudFoundryClientFactory clientFactory;
	private CloudFoundryTestHarness harness;
	private BootDashActions actions;

	////////////////////////////////////////////////////////////

	public CloudFoundryApplicationHarness appHarness = new CloudFoundryApplicationHarness(null);

	@Rule
	public AutobuildingEnablement disableAutoBuild = new AutobuildingEnablement(false);

	@Rule
	public TestBracketter testBracketter = new TestBracketter();

	private SpringBootCore springBootCore = SpringBootCore.getDefault();

	@Before
	public void setup() throws Exception {
		StsTestUtil.deleteAllProjects();
		this.ui = mock(UserInteractions.class);
		this.context = new TestBootDashModelContext(
				ResourcesPlugin.getWorkspace(),
				DebugPlugin.getDefault().getLaunchManager(),
				ui
		);
		this.clientFactory = new MockCloudFoundryClientFactory();
		this.harness = CloudFoundryTestHarness.create(context, clientFactory);
		this.projects = new BootProjectTestHarness(context.getWorkspace());
		this.actions = new BootDashActions(harness.model, harness.selection.forReading(), harness.sectionSelection, ui);
	}

	@After
	public void tearDown() throws Exception {
		waitForJobsToComplete();
		appHarness.dispose();
		clientFactory.assertOnlyImplementedStubsCalled();
		harness.dispose();
	}

	////////////////////////////////////////////////////////////

	@Test
	public void testCreateCfTarget() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_PASSWORD);

		assertNotNull(target);
		CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
		assertEquals(CFCredentialType.PASSWORD, credentials.getType());
		assertNotNull(credentials.getSecret());
		assertEquals(1, harness.getCfRunTargetModels().size());
	}

	@Test public void refreshTokenStreamTerminatedOnDispose() throws Exception {
		CFClientParams params = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(params.getOrgName(), params.getSpaceName());
		ClientRequests client = clientFactory.getClient(params);

		CountDownLatch latch = new CountDownLatch(1);
		Future<List<String>> tokens = runInJob(() -> {
			return client.getRefreshTokens()
					.doOnNext(token -> latch.countDown())
					.collect(Collectors.toList()).block(Duration.ofSeconds(10));
		});
		client.getApplicationsWithBasicInfo(); // forces the client to authenticate
		latch.await(); //To avoid race condition (job tends to be slow and might start asking tokens only after disposed client (and this yields a empty stream of tokens)
		client.dispose();
		assertEquals(ImmutableList.of(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN), tokens.get());
	}


	@Test
	public void testCreateCfTargetSsoAndStoreRefreshToken() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnvWithCredentials(CFCredentials.fromSsoToken(clientFactory.getSsoToken()));
		{
			clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
			CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_TOKEN);

			assertNotNull(target);
			CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
			assertEquals(CFCredentialType.REFRESH_TOKEN, credentials.getType());
			assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, credentials.getSecret());
			assertEquals(1, harness.getCfRunTargetModels().size());
		}
		harness.reload();

		CloudFoundryBootDashModel target = harness.getCfTargetModel();
		assertNotNull(target);

		CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
		assertEquals(CFCredentialType.REFRESH_TOKEN, credentials.getType());
		assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, credentials.getSecret());
		assertEquals(StoreCredentialsMode.STORE_TOKEN, target.getRunTarget().getTargetProperties().getStoreCredentials());

		waitForJobsToComplete();
		assertTrue(target.isConnected()); //should auto connect.
		verifyZeroInteractions(ui); //should not prompt for password (but used stored pass).

		{
			SecuredCredentialsStore store = harness.getCredentialsStore();
			assertFalse(store.isUnlocked()); //not unlocked because token is stored in simple private file
		}

		{
			String storedCred = getStoredToken(target);
			assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, storedCred);
		}

		clientFactory.changeRefrestToken("another-1");
		clientFactory.changeRefrestToken("another-2");
		ACondition.waitFor("changed stored token", 300, () -> {
			assertEquals("another-2", getStoredToken(target));
		});
	}

	private String getStoredToken(CloudFoundryBootDashModel target) {
		IPropertyStore store = harness.getPrivateStore();
		String key = harness.privateStoreKey(target);
		String storedCred = store.get(key);
		return storedCred;
	}

	@Test
	public void testCreateCfTargetSsoAndStoreNothing() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnvWithCredentials(CFCredentials.fromSsoToken(clientFactory.getSsoToken()));
		{
			clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
			CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_NOTHING);

			assertNotNull(target);
			CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
			assertEquals(CFCredentialType.REFRESH_TOKEN, credentials.getType());
			assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, credentials.getSecret());
			assertEquals(1, harness.getCfRunTargetModels().size());
		}
		harness.reload();

		CloudFoundryBootDashModel target = harness.getCfTargetModel();
		assertNotNull(target);

		CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
		assertNull(credentials);

		waitForJobsToComplete();
		assertFalse(target.isConnected()); //should not auto connect.
		verifyZeroInteractions(ui);

		{
			SecuredCredentialsStore store = harness.getCredentialsStore();
			assertFalse(store.isUnlocked()); //not unlocked because token is stored in simple private file
			assertNull(store.getCredentials(harness.secureStoreKey(target)));
		}

		{
			IPropertyStore store = harness.getPrivateStore();
			String key = harness.privateStoreKey(target);
			String storedCred = store.get(key);
			assertNull(storedCred);
		}
	}

	@Test
	public void testCreateCfTargetSsoAndStorePassword() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnvWithCredentials(CFCredentials.fromSsoToken(clientFactory.getSsoToken()));
		{
			clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
			CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_PASSWORD, (wizard) -> {
				assertContains("'Store Password' is useless for a 'Temporary Code'", wizard.getValidator().getValue().msg);
			});
			//STORE_PASSWORD is meaningless for sso login and should be ignored (so behaves as STORE_NOTHING instead)

			assertNotNull(target);
			CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
			assertEquals(CFCredentialType.REFRESH_TOKEN, credentials.getType());
			assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, credentials.getSecret());
			assertEquals(1, harness.getCfRunTargetModels().size());
		}
		harness.reload();

		CloudFoundryBootDashModel target = harness.getCfTargetModel();
		assertNotNull(target);

		CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
		assertNull(credentials);

		waitForJobsToComplete();
		assertFalse(target.isConnected()); //should not auto connect.
		verifyZeroInteractions(ui);

		{
			SecuredCredentialsStore store = harness.getCredentialsStore();
			assertFalse(store.isUnlocked()); //not unlocked because token is stored in simple private file
			assertNull(store.getCredentials(harness.secureStoreKey(target)));
		}

		{
			IPropertyStore store = harness.getPrivateStore();
			String key = harness.privateStoreKey(target);
			String storedCred = store.get(key);
			assertNull(storedCred);
		}
	}

	@Test public void testCreateCfTargetAndStorePassword() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		{
			clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
			CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_PASSWORD);

			assertNotNull(target);
			CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
			assertEquals(CFCredentialType.PASSWORD, credentials.getType());
			assertNotNull(credentials.getSecret());
			assertEquals(1, harness.getCfRunTargetModels().size());

			SecuredCredentialsStore store = harness.getCredentialsStore();
			assertTrue(store.isUnlocked());
		}

		harness.reload();
		{
			CloudFoundryBootDashModel target = harness.getCfTargetModel();
			String expectedPass = targetParams.getCredentials().getSecret();

			assertNotNull(target);
			CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
			String password = credentials.getSecret();
			assertEquals(CFCredentialType.PASSWORD, credentials.getType());
			assertEquals(expectedPass, password);
			assertEquals(StoreCredentialsMode.STORE_PASSWORD, target.getRunTarget().getTargetProperties().getStoreCredentials());

			waitForJobsToComplete();
			assertTrue(target.isConnected()); //should auto connect.
			verifyZeroInteractions(ui); //should not prompt for password (but used stored pass).

			{
				SecuredCredentialsStore store = harness.getCredentialsStore();
				assertTrue(store.isUnlocked());
				String key = harness.secureStoreKey(target);
				String storedCred = store.getCredentials(key);
				assertEquals(expectedPass, storedCred);
			}

			{
				IPropertyStore store = harness.getPrivateStore();
				String key = harness.privateStoreKey(target);
				String storedCred = store.get(key);
				assertNull(storedCred);
			}
		}
	}

	@Test public void testCreateCfTargetAndForgetPassword() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		{
			CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_NOTHING);

			assertNotNull(target);
			CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
			assertEquals(CFCredentialType.REFRESH_TOKEN, credentials.getType());
			assertNotNull(credentials.getSecret());
			assertEquals(1, harness.getCfRunTargetModels().size());

			SecuredCredentialsStore store = harness.getCredentialsStore();
			assertFalse(store.isUnlocked()); // should not have gotten unlocked.
		}

		harness.reload();

		{
			CloudFoundryBootDashModel target = harness.getCfTargetModel();

			waitForJobsToComplete();

			assertEquals(StoreCredentialsMode.STORE_NOTHING, target.getRunTarget().getTargetProperties().getStoreCredentials());
			assertNotNull(target);
			assertNull(target.getRunTarget().getTargetProperties().getCredentials());
			assertFalse(target.isConnected()); // no auto connect if no creds are stored.
			{	//check secure store is clean
				SecuredCredentialsStore store = harness.getCredentialsStore();
				assertFalse(store.isUnlocked()); // should not have gotten unlocked.
				String storedCred = store.getCredentials(harness.secureStoreKey(target));
				assertNull(storedCred);
			}
			{	//check private store is clean
				IPropertyStore store = harness.getPrivateStore();
				String storedCred = store.get(harness.privateStoreKey(target));
				assertNull(storedCred);
			}

			verifyZeroInteractions(ui);

			//When we connect... the user should get prompted for password
			harness.answerPasswordPrompt(ui, (d) -> {
				d.getMethodVar().setValue(targetParams.getCredentials().getType().toLoginMethod());
				d.getPasswordVar().setValue(targetParams.getCredentials().getSecret());
				d.validateCredentials().block();
				d.performOk();
			});

			harness.sectionSelection.setValue(target);
			IAction connectAction = actions.getToggleTargetConnectionAction();
			connectAction.run();

			ACondition.waitFor("connected to target", 5_000, () -> {
				assertTrue(target.isConnected());
			});

			verify(ui).openPasswordDialog(any());
			verifyNoMoreInteractions(ui);
		}
	}

	@Test public void testCreateCfTargetAndStoreToken() throws Exception {

		{
			CFClientParams targetParams = CfTestTargetParams.fromEnv();
			MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
			space.defApp("foo");
			clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

			CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_TOKEN);
			assertNotNull(target);
			assertTrue(target.isConnected());
			CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
			assertEquals(CFCredentialType.REFRESH_TOKEN, credentials.getType());
			assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, credentials.getSecret());
			assertEquals(1, harness.getCfRunTargetModels().size());
		}

		harness.reload();

		{
			CloudFoundryBootDashModel target = harness.getCfTargetModel();

			{	//secure store shouldn't have been accessed (i.e. avoid opening it and popping password)
				SecuredCredentialsStore store = harness.getCredentialsStore();
				assertFalse(store.isUnlocked());
				String key = harness.secureStoreKey(target);
				String storedCred = store.getCredentials(key);
				assertNull(storedCred);
			}
			{
				IPropertyStore store = harness.getPrivateStore();
				String key = harness.privateStoreKey(target);
				String storedCred = store.get(key);
				assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, storedCred);
			}

			assertNotNull(target);
			CFCredentials credentials = target.getRunTarget().getTargetProperties().getCredentials();
			assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, credentials.getSecret());
			assertEquals(CFCredentialType.REFRESH_TOKEN, credentials.getType());
			assertEquals(StoreCredentialsMode.STORE_TOKEN, target.getRunTarget().getTargetProperties().getStoreCredentials());

			waitForJobsToComplete();
			assertTrue(target.isConnected()); //should auto connect.
			verifyZeroInteractions(ui); //should not prompt for password (but used stored token).

			clientFactory.changeRefrestToken("another-1");
			clientFactory.changeRefrestToken("another-2");
			ACondition.waitFor("changed stored token", 300, () -> {
				assertEquals("another-2", getStoredToken(target));
			});
		}
	}

	@Test
	public void testAppsShownInBootDash() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();

		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		space.defApp("foo");
		space.defApp("bar");

		final CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams);

		new ACondition("wait for apps to appear", 3000) {
			@Override
			public boolean test() throws Exception {
				ImmutableSet<String> appNames = getNames(target.getApplications().getValues());
				assertEquals(ImmutableSet.of("foo", "bar"), appNames);
				return true;
			}
		};
	}

	@Test
	public void testBasicRefreshApps() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();

		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		space.defApp("foo");
		space.defApp("bar");

		final CloudFoundryBootDashModel target = harness.createCfTarget(targetParams);

		waitForApps(target, "foo", "bar");

		space.defApp("anotherfoo");
		space.defApp("anotherbar");
		target.refresh(ui);

		waitForApps(target, "foo", "bar", "anotherfoo", "anotherbar");
	}

	@Test
	public void testRefreshAppsRunState() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();

		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		final MockCFApplication foo = space.defApp("foo");
		space.defApp("bar");

		final CloudFoundryBootDashModel target = harness.createCfTarget(targetParams);

		waitForApps(target, "foo", "bar");

		foo.start(CancelationTokens.NULL);

		target.refresh(ui);

		new ACondition("wait for app states", 3000) {
			@Override
			public boolean test() throws Exception {
				ImmutableSet<String> appNames = getNames(target.getApplications().getValues());
				assertEquals(ImmutableSet.of("foo", "bar"), appNames);
				CloudAppDashElement appElement = harness.getCfTargetModel().getApplication("foo");
				assertEquals(RunState.RUNNING, appElement.getRunState());

				appElement = harness.getCfTargetModel().getApplication("bar");
				assertEquals(RunState.INACTIVE, appElement.getRunState());

				return true;
			}
		};
	}

	@Test
	public void testRefreshAppsHealthCheck() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();

		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		final MockCFApplication foo = space.defApp("foo");


		final CloudFoundryBootDashModel target = harness.createCfTarget(targetParams);

		waitForApps(target, "foo");

		CloudAppDashElement appElement = harness.getCfTargetModel().getApplication("foo");
		assertEquals(HealthChecks.HC_PORT, appElement.getHealthCheck());


		foo.setHealthCheckType(HealthChecks.HC_NONE);

		target.refresh(ui);

		new ACondition("wait for app health check", 3000) {
			@Override
			public boolean test() throws Exception {
				ImmutableSet<String> appNames = getNames(target.getApplications().getValues());
				assertEquals(ImmutableSet.of("foo"), appNames);

				CloudAppDashElement appElement = harness.getCfTargetModel().getApplication("foo");
				assertEquals(HealthChecks.HC_NONE, appElement.getHealthCheck());

				return true;
			}
		};
	}

	@Test
	public void testRefreshServices() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();

		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		space.defApp("foo");
		space.defService("elephantsql");
		space.defService("cleardb");

		final CloudFoundryBootDashModel target = harness.createCfTarget(targetParams);

		waitForApps(target, "foo");
		waitForServices(target, "elephantsql", "cleardb");
		waitForElements(target, "foo", "elephantsql", "cleardb");

		space.defService("rabbit");

		target.refresh(ui);
		waitForServices(target, "elephantsql", "cleardb", "rabbit");
		waitForElements(target, "foo", "elephantsql", "cleardb", "rabbit");
	}

	@Test
	public void testAppsAndServicesShownInBootDash() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();

		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		space.defApp("foo");
		space.defApp("bar");
		space.defService("a-sql");
		space.defService("z-rabbit");

		final CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams);
		assertTrue(target.isConnected());

		debugListener("applications", target.getApplications());
		debugListener("services", target.getServices());
		debugListener("all", target.getElements());

		new ACondition("wait for elements to appear", 3000) {
			@Override
			public boolean test() throws Exception {
				ImmutableSet<String> appNames = getNames(target.getApplications().getValues());
				ImmutableSet<String> serviceNames = getNames(target.getServices().getValues());
				ImmutableSet<String> allNames = getNames(target.getElements().getValues());
				assertEquals(ImmutableSet.of("foo", "bar"), appNames);
				assertEquals(ImmutableSet.of("a-sql", "z-rabbit"), serviceNames);
				assertEquals(ImmutableSet.of("foo", "bar", "a-sql", "z-rabbit"), allNames);
				return true;
			}
		};

		//Also test we sort this stuff in the right order.

		ArrayList<BootDashElement> elements = new ArrayList<>(target.getElements().getValues());
		Collections.sort(elements, target.getElementComparator());
		assertNames(elements,
				//first apps... alphabetic
				"bar",
				"foo",
				//then services... alphabetic
				"a-sql",
				"z-rabbit"
		);

		//For https://www.pivotaltracker.com/story/show/114408475
		// Apps and services should disappear when target is disconnected

		IAction toggleConnection = actions.getToggleTargetConnectionAction();
		harness.sectionSelection.setValue(target);
		toggleConnection.run();

		new ACondition("wait for elements to disappear", 10000) {
			@Override
			public boolean test() throws Exception {
				assertFalse(target.isConnected());
				ImmutableSet<String> appNames = getNames(target.getApplications().getValues());
				ImmutableSet<String> serviceNames = getNames(target.getServices().getValues());
 				ImmutableSet<String> allNames = getNames(target.getElements().getValues());
 				assertEquals(ImmutableSet.of(), appNames);
 				assertEquals(ImmutableSet.of(), serviceNames);
 				assertEquals(ImmutableSet.of(), allNames);
				return true;
			}
		};
	}


	@Test
	public void testDeployActionsSorted() throws Exception {
		//Generate some random 'space' names.
		String orgName = "CloudRunAMock";
		String[] spaceNames = new String[6];
		for (int i = 0; i < spaceNames.length; i++) {
			spaceNames[i] = RandomStringUtils.randomAlphabetic(10).toLowerCase();
		}

		//Define the spaces in the 'mock' cloud:
		for (String spaceName : spaceNames) {
			//Since this is just a mock client we creating, the params don't matter all that much at all.
			clientFactory.defSpace(orgName, spaceName);
		}

		//Create targets in the boot dash that connect to these spaces:
		for (String spaceName : spaceNames) {
			CFClientParams params = new CFClientParams(
					"http://api.run.cloud.mock.com",
					"some-user",  CFCredentials.fromPassword(MockCloudFoundryClientFactory.FAKE_PASSWORD),
					false,
					orgName,
					spaceName,
					false
			);
			harness.createCfTarget(params);
		}

		{
			ImmutableList<IAction> deployActions = actions.getDebugOnTargetActions();
			assertEquals(spaceNames.length, deployActions.size());
			assertSorted(deployActions);
		}

		{
			ImmutableList<IAction> deployActions = actions.getRunOnTargetActions();
			assertEquals(spaceNames.length, deployActions.size());
			assertSorted(deployActions);
		}

	}

	@Test
	public void targetTypeProperties() throws Exception {
		{
			CloudFoundryRunTargetType cfTargetType = harness.getCfTargetType();
			PropertyStoreApi props = cfTargetType.getPersistentProperties();
			props.put("testkey", "testvalue");
			assertEquals("testvalue", props.get("testkey"));
		}

		harness.reload();

		{
			CloudFoundryRunTargetType cfTargetType = harness.getCfTargetType();
			PropertyStoreApi props = cfTargetType.getPersistentProperties();
			assertEquals("testvalue", props.get("testkey"));
		}

	}

	@Test
	public void appsManagerDefaultHost() throws Exception {
		MockCFSpace space = clientFactory.defSpace("my-org", "foo");

		String apiUrl = "http://api.some-cloud.com";
		String username = "freddy"; String password = MockCloudFoundryClientFactory.FAKE_PASSWORD;

		CloudFoundryBootDashModel cfModel = harness.createCfTarget(new CFClientParams(
				apiUrl,
				username, CFCredentials.fromPassword(password),
				false,
				"my-org",
				"foo",
		false));

		assertEquals("http://console.some-cloud.com", cfModel.getRunTarget().getAppsManagerHost());
		assertEquals("http://console.some-cloud.com", cfModel.getRunTarget().getAppsManagerHostDefault());

		assertEquals("http://console.some-cloud.com/organizations/" + space.getOrganization().getGuid() + "/spaces/" + space.getGuid(), cfModel.getRunTarget().getAppsManagerURL());
	}

	@Test
	public void appsManagerCustomizedHost() throws Exception {
		MockCFSpace space = clientFactory.defSpace("my-org", "foo");

		String apiUrl = "http://api.some-cloud.com";
		String username = "freddy"; String password = MockCloudFoundryClientFactory.FAKE_PASSWORD;

		CloudFoundryBootDashModel cfModel = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "my-org", "foo", false
		));

		cfModel.getRunTarget().setAppsManagerHost("http://totallyDifferentHost.com");

		assertEquals("http://totallyDifferentHost.com", cfModel.getRunTarget().getAppsManagerHost());
		assertEquals("http://console.some-cloud.com", cfModel.getRunTarget().getAppsManagerHostDefault());

		assertEquals("http://totallyDifferentHost.com/organizations/" + space.getOrganization().getGuid() + "/spaces/" + space.getGuid(), cfModel.getRunTarget().getAppsManagerURL());
	}

	@Test
	public void templateDrivenTargetNames() throws Exception {
		clientFactory.defSpace("my-org", "foo");
		clientFactory.defSpace("your-org", "bar");

		String apiUrl = "http://api.some-cloud.com";
		String username = "freddy"; String password = MockCloudFoundryClientFactory.FAKE_PASSWORD;
		AbstractBootDashModel fooSpace = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "my-org", "foo", false));
		AbstractBootDashModel barSpace = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "your-org", "bar", false));

		//check the default rendering is like it used to be before introducing templates.
		assertEquals("my-org : foo - [http://api.some-cloud.com]", fooSpace.getDisplayName());
		assertEquals("your-org : bar - [http://api.some-cloud.com]", barSpace.getDisplayName());

		RunTargetType targetType = fooSpace.getRunTarget().getType();

		//Let's try switching the order of org and space
		targetType.setNameTemplate("%s - %o @ %a");
		assertEquals("foo - my-org @ http://api.some-cloud.com", fooSpace.getDisplayName());
		assertEquals("bar - your-org @ http://api.some-cloud.com", barSpace.getDisplayName());

		//Let's try adding 'username' into the label
		targetType.setNameTemplate("%u@%s");
		assertEquals("freddy@foo", fooSpace.getDisplayName());
		assertEquals("freddy@bar", barSpace.getDisplayName());
	}

	@Test public void pushTcpRouteWithRandomPort() throws Exception {
		String appName = "moriarty-app";
		String apiUrl = "http://api.some-cloud.com";
		String username = "freddy"; String password = MockCloudFoundryClientFactory.FAKE_PASSWORD;

		MockCFSpace space = clientFactory.defSpace("my-org", "my-space");
		clientFactory.defDomain("tcp.domain.com", CFDomainType.TCP, CFDomainStatus.SHARED);

		assertEquals("cfmockapps.io", clientFactory.getDefaultDomain());

		CloudFoundryBootDashModel target = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "my-org", "my-space", false));
		IProject project = projects.createBootProject("to-deploy", withStarters("web", "actuator"));
		IFile manifest = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n" +
				"  random-route: true\n" +
				"  domain: tcp.domain.com"
		);
		harness.answerDeploymentPrompt(ui, manifest);
		target.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
		waitForApps(target, appName);
		CloudAppDashElement app = getApplication(target, project);
		waitForState(app, RunState.RUNNING, 10_000);

		MockCFApplication deployedApp = space.getApplication(appName);
		List<String> uris = deployedApp.getBasicInfo().getUris();
		assertEquals(ImmutableList.of("tcp.domain.com:63000"), uris);

		doUnchangedAppRestartTest(app, deployedApp);
	}

	@Test public void pushTcpRouteWithFixedPort() throws Exception {
		String appName = "moriarty-app";
		String apiUrl = "http://api.some-cloud.com";
		String username = "freddy"; String password = MockCloudFoundryClientFactory.FAKE_PASSWORD;

		MockCFSpace space = clientFactory.defSpace("my-org", "my-space");
		clientFactory.defDomain("tcp.domain.com", CFDomainType.TCP, CFDomainStatus.SHARED);

		CloudFoundryBootDashModel target = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "my-org", "my-space", false));
		IProject project = projects.createBootProject("to-deploy", withStarters("web", "actuator"));
		IFile manifest = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n" +
				"  routes: \n" +
				"  - route: tcp.domain.com:61001\n"
		);
		harness.answerDeploymentPrompt(ui, manifest);
		target.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
		waitForApps(target, appName);
		CloudAppDashElement app = getApplication(target, project);
		waitForState(app, RunState.RUNNING, 10_000);

		MockCFApplication deployedApp = space.getApplication(appName);
		List<String> uris = deployedApp.getBasicInfo().getUris(); //Note: this info isn't retrieved via client!
		assertEquals(ImmutableList.of("tcp.domain.com:61001"), uris); //Note: this info wasn't retrieved via client! Check if client info agrees
		assertEquals(uris, target.getClient().getApplication(appName).getUris()); //Note: this info wasn't retrieved via client! Check if client info agrees

		doUnchangedAppRestartTest(app, deployedApp);
	}

	@Test public void pushHttpRouteWithRandomRoute() throws Exception {
		String appName = "moriarty-app";
		String apiUrl = "http://api.some-cloud.com";
		String username = "freddy"; String password = MockCloudFoundryClientFactory.FAKE_PASSWORD;

		MockCFSpace space = clientFactory.defSpace("my-org", "my-space");
		clientFactory.defDomain("tcp.domain.com", CFDomainType.TCP, CFDomainStatus.SHARED);

		assertEquals("cfmockapps.io", clientFactory.getDefaultDomain());

		CloudFoundryBootDashModel target = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "my-org", "my-space", false));
		IProject project = projects.createBootProject("to-deploy", withStarters("web", "actuator"));
		IFile manifest = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n" +
				"  random-route: true\n"
		);
		harness.answerDeploymentPrompt(ui, manifest);
		target.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
		waitForApps(target, appName);
		CloudAppDashElement app = getApplication(target, project);
		waitForState(app, RunState.RUNNING, 10_000);

		MockCFApplication deployedApp = space.getApplication(appName);
		List<String> uris = deployedApp.getBasicInfo().getUris();
		assertTrue(uris.size() == 1);
		assertTrue(uris.get(0).length() > ".cfmockapps.io".length());
		assertTrue(uris.get(0).endsWith(".cfmockapps.io"));

		doUnchangedAppRestartTest(app, deployedApp);
	}

	@Test
	public void customizeTargetLabelAction() throws Exception {
		clientFactory.defSpace("my-org", "foo");
		clientFactory.defSpace("your-org", "bar");

		String apiUrl = "http://api.some-cloud.com";
		String username = "freddy"; String password = MockCloudFoundryClientFactory.FAKE_PASSWORD;
		LocalBootDashModel local = harness.getLocalModel();
		AbstractBootDashModel fooSpace = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "my-org", "foo", false));
		AbstractBootDashModel barSpace = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "your-org", "bar", false));
		CustmomizeTargetLabelAction action = actions.getCustomizeTargetLabelAction();

		//////////// not applicable for local targets:

		harness.sectionSelection.setValue(local);
		assertFalse(action.isEnabled());
		assertFalse(action.isVisible());

		//////////// for cf targets //////////////////////////////////////////////////

		harness.sectionSelection.setValue(fooSpace);
		assertTrue(action.isEnabled());
		assertTrue(action.isVisible());

		ModelStateListener modelStateListener = mock(ModelStateListener.class);
		fooSpace.addModelStateListener(modelStateListener);
		barSpace.addModelStateListener(modelStateListener);

		doAnswer(editSetTemplate("%s - %o @ %a"))
			.when(ui).openEditTemplateDialog(any(EditTemplateDialogModel.class));

		action.run();

		//Changing the template should result in modelStateListener firing on all the affected models
		verify(modelStateListener).stateChanged(same(fooSpace));
		verify(modelStateListener).stateChanged(same(barSpace));

		assertEquals("foo - my-org @ http://api.some-cloud.com", fooSpace.getDisplayName());
		assertEquals("bar - your-org @ http://api.some-cloud.com", barSpace.getDisplayName());

		//Let's also try a user interaction that involves the 'Restore Defaults' button...

		reset(ui, modelStateListener);

		doAnswer(restoreDefaultTemplate())
			.when(ui).openEditTemplateDialog(any(EditTemplateDialogModel.class));

		action.run();

		verify(modelStateListener).stateChanged(same(fooSpace));
		verify(modelStateListener).stateChanged(same(barSpace));

		assertEquals("my-org : foo - [http://api.some-cloud.com]", fooSpace.getDisplayName());
		assertEquals("your-org : bar - [http://api.some-cloud.com]", barSpace.getDisplayName());
	}

	@Test
	public void customizeTargetLabelDialog() throws Exception {
		EditTemplateDialogModel dialog;
		clientFactory.defSpace("my-org", "foo");
		clientFactory.defSpace("your-org", "bar");

		String apiUrl = "http://api.some-cloud.com";
		String username = "freddy"; String password = MockCloudFoundryClientFactory.FAKE_PASSWORD;

		AbstractBootDashModel fooSpace = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "my-org", "foo", false));
		AbstractBootDashModel barSpace = harness.createCfTarget(new CFClientParams(apiUrl, username,
				CFCredentials.fromPassword(password), false, "your-org", "bar", false));

		ModelStateListener modelStateListener = mock(ModelStateListener.class);
		fooSpace.addModelStateListener(modelStateListener);
		barSpace.addModelStateListener(modelStateListener);

		// Check initial state of the dialog when no custom labels have yet been set at all:
		dialog = CustomizeTargetLabelDialogModel.create(fooSpace);

		assertTrue(dialog.applyToAll.getValue());
		assertEquals("%o : %s - [%a]", dialog.template.getValue());

		//Check performOk only changes the one label when 'apply to all' is disabled.
		dialog.applyToAll.setValue(false);
		dialog.template.setValue("CHANGED %s -> %o");
		dialog.performOk();

		assertEquals("CHANGED foo -> my-org", fooSpace.getDisplayName());
		assertEquals("your-org : bar - [http://api.some-cloud.com]", barSpace.getDisplayName());

		verify(modelStateListener).stateChanged(same(fooSpace));
		verify(modelStateListener, never()).stateChanged(same(barSpace));

		//Opening the dialog now should have 'apply to all' disabled to avoid accidentally overwriting
		// existing individually customized labels...
		dialog = CustomizeTargetLabelDialogModel.create(fooSpace);
		assertFalse(dialog.applyToAll.getValue());
		assertEquals("CHANGED %s -> %o", dialog.template.getValue());

		//Also if we open the dialog on the other element!!!
		dialog = CustomizeTargetLabelDialogModel.create(barSpace);
		assertFalse(dialog.applyToAll.getValue());
		assertEquals("%o : %s - [%a]", dialog.template.getValue());

		//Selecting 'apply to all' should set the template on the type and erase custom templates on the
		// individual targets.
		dialog.applyToAll.setValue(true);
		dialog.template.setValue("DIFFERENT %s -> %o");
		dialog.performOk();

		assertEquals("DIFFERENT %s -> %o", harness.getCfTargetType().getNameTemplate());
		for (BootDashModel target : harness.getCfRunTargetModels()) {
			assertFalse(target.hasCustomNameTemplate());
			assertEquals("DIFFERENT %s -> %o", target.getNameTemplate());
		}

		assertEquals("DIFFERENT foo -> my-org", fooSpace.getDisplayName());
		assertEquals("DIFFERENT bar -> your-org", barSpace.getDisplayName());
	}

	@Test
	public void testPushWithHttpHealthCheckType() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams);
		final CloudFoundryBootDashModel model = harness.getCfTargetModel();

		IProject project = projects.createBootProject("to-deploy", withStarters("actuator", "web"));
		final String appName = appHarness.randomAppName();

		IFile manifest = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n" +
				"  health-check-type: http\n" +
				"  health-check-http-endpoint: /health\n"
		);
		harness.answerDeploymentPrompt(ui, manifest);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		ACondition.waitFor("wait for app '"+ appName +"'to be RUNNING", 30000, () -> {
			CloudAppDashElement app = model.getApplication(appName);
			assertEquals(RunState.RUNNING, app.getRunState());
			assertEquals("http", app.getHealthCheck());
			assertEquals("/health", app.getHealthCheckHttpEndpoint());
		});
	}

	@Test
	public void testEnvVarsSetOnFirstDeploy() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams);
		final CloudFoundryBootDashModel model = harness.getCfTargetModel();

		IProject project = projects.createBootProject("to-deploy", withStarters("actuator", "web"));

		final String appName = appHarness.randomAppName();

		Map<String, String> env = new HashMap<>();
		env.put("FOO", "something");
		harness.answerDeploymentPrompt(ui, appName, appName, env);

		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		new ACondition("wait for app '"+ appName +"'to be RUNNING", 30000) { //why so long? JDT searching for main type.
			public boolean test() throws Exception {
				CloudAppDashElement element = model.getApplication(appName);
				assertEquals(RunState.RUNNING, element.getRunState());
				return true;
			}
		};

		Map<String,String> actualEnv = harness.fetchEnvironment(target, appName);

		assertEquals("something", actualEnv.get("FOO"));
	}

	@Test public void appToProjectBindingsPersisted() throws Exception {
		final String appName = "foo";
		String projectName = "to-deploy";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		final IProject project = projects.createBootProject(projectName, withStarters("actuator", "web"));

		harness.createCfTarget(targetParams);
		{
			final CloudFoundryBootDashModel model = harness.getCfTargetModel();

			deployApp(model, appName, project);

			CloudAppDashElement appByProject = getApplication(model, project);
			CloudAppDashElement appByName = model.getApplication(appName);
			assertNotNull(appByProject);
			assertEquals(appByProject, appByName);
		}

		harness.reload();
		{
			final CloudFoundryBootDashModel model = harness.getCfTargetModel();
			waitForApps(model, appName);
			CloudAppDashElement appByName = model.getApplication(appName);
			CloudAppDashElement appByProject = getApplication(model, project);
			assertNotNull(appByProject);
			assertEquals(appByProject, appByName);
		}
	}

	@Test public void appToProjectBindingsPersistedAfterDisconnectConnect() throws Exception {
		final String appName = "foo";
		String projectName = "to-deploy";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		final IProject project = projects.createBootProject(projectName, withStarters("actuator", "web"));

		final CloudFoundryBootDashModel model = harness.createCfTarget(targetParams);
		deployApp(model, appName, project);
		assertAppToProjectBinding(model, project, appName);

		IAction toggleConnectionAction = actions.getToggleTargetConnectionAction();
		harness.sectionSelection.setValue(model);

		toggleConnectionAction.run();
		waitForElements(model /*none*/);
		toggleConnectionAction.run();
		waitForElements(model, appName);

		assertAppToProjectBinding(model, project, appName);
	}

	@Test public void appToProjectBindingChangedAfterProjectRename() throws Exception {
		final String appName = "foo";
		String projectName = "to-deploy";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		space.defApp(appName);
		final IProject project = projects.createProject(projectName);

		final CloudFoundryBootDashModel target = harness.createCfTarget(targetParams);
		waitForApps(target, appName);
		CloudAppDashElement app = target.getApplication(appName);
		app.setProject(project);

		assertAppToProjectBinding(target, project, appName);


		ElementStateListener elementStateListener = mock(ElementStateListener.class);
		target.addElementStateListener(elementStateListener);

		final IProject newProject = projects.rename(project, projectName+"-RENAMED");
		// resource listeners called synchronously by eclipse so we don't need ACondition

		assertAppToProjectBinding(target, newProject, appName);

		//state change event should have been fired (to update label of element in view)
		verify(elementStateListener).stateChanged(same(app));
	}

	@Test public void appToProjectBindingForgottenAfterDelete() throws Exception {
		final String appName = "foo";
		String projectName = "to-deploy";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		space.defApp(appName);
		final IProject project = projects.createProject(projectName);

		final CloudFoundryBootDashModel target = harness.createCfTarget(targetParams);
		waitForApps(target, appName);
		CloudAppDashElement app = target.getApplication(appName);
		app.setProject(project);

		assertAppToProjectBinding(target, project, appName);

		ElementStateListener elementStateListener = mock(ElementStateListener.class);
		target.addElementStateListener(elementStateListener);

		project.delete(true, new NullProgressMonitor());

		assertNull(app.getProject(true));
		verify(elementStateListener).stateChanged(same(app));
	}

	@Test public void runstateBecomesUnknownWhenStartOperationFails() throws Exception {
		final String appName = "foo";
		String projectName = "to-deploy";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		MockCFApplication app = space.defApp(appName);
		final IProject project = projects.createProject(projectName);

		final CloudFoundryBootDashModel target = harness.createCfTarget(targetParams);
		waitForApps(target, appName);
		CloudAppDashElement appElement = target.getApplication(appName);
		appElement.setProject(project);

		//The state refressh is asynch so until state becomes 'INACTIVE' it will be unknown.
		waitForState(appElement, RunState.INACTIVE, 3000);
		IAction restartAction = actions.getRestartOnlyApplicationAction();

		RunStateHistory runstateHistory = new RunStateHistory();

		appElement.getBaseRunStateExp().addListener(runstateHistory);
		doThrow(IOException.class).when(app).start(any());

		System.out.println("restarting application...");
		harness.selection.setElements(appElement);
		restartAction.run();

		waitForState(appElement, RunState.UNKNOWN, 3000);

		runstateHistory.assertHistoryContains(
				RunState.INACTIVE,
				RunState.STARTING
		);
		runstateHistory.assertLast(
				RunState.UNKNOWN
		);
	}

	@Test public void refreshClearsErrorState() throws Exception {
		final String appName = "foo";
		String projectName = "to-deploy";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		space.defApp(appName);
		final IProject project = projects.createProject(projectName);

		final CloudFoundryBootDashModel target = harness.createCfTarget(targetParams);
		waitForApps(target, appName);
		CloudAppDashElement appElement = target.getApplication(appName);
		appElement.setProject(project);

		waitForState(appElement, RunState.INACTIVE, 3000);
		//The state refressh is asynch so until state becomes 'INACTIVE' it will be unknown.
		appElement.setError(new IOException("Something bad happened"));
		waitForState(appElement, RunState.UNKNOWN, 3000);

		target.refresh(ui);

		waitForState(appElement, RunState.INACTIVE, 3000);
	}

	@Test public void pushSimple() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);

		IProject project = projects.createBootProject("to-deploy", withStarters("actuator", "web"));

		final String appName = appHarness.randomAppName();

		harness.answerDeploymentPrompt(ui, appName, appName);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
		waitForApps(model, appName);

		CloudAppDashElement app = model.getApplication(appName);

		waitForState(app, RunState.RUNNING, 10000);

		assertEquals((Integer)1, space.getPushCount(appName).getValue());

		MockCFApplication deployedApp = space.getApplication(appName);
		doUnchangedAppRestartTest(app, deployedApp);
	}

	@Test public void pushSimpleWithManifest() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);

		IProject project = projects.createBootProject("to-deploy", withStarters("actuator", "web"));

		final String appName = appHarness.randomAppName();

		IFile manifestFile = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n" +
				"  memory: 512M\n"
		);

		harness.answerDeploymentPrompt(ui, (dialog) -> {
			dialog.okPressed();
		});

		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		waitForApps(model, appName);

		CloudAppDashElement app = model.getApplication(appName);

		waitForState(app, RunState.RUNNING, 10000);

		assertEquals((Integer)1, space.getPushCount(appName).getValue());
		assertEquals(manifestFile, app.getDeploymentManifestFile());
		assertEquals(512, (int) app.getMemory());


		MockCFApplication deployedApp = space.getApplication(appName);
		CFApplication appInfo = deployedApp.getBasicInfo();
		List<String> uris = appInfo.getUris();
		assertTrue(uris.size() == 1);
		assertEquals(ImmutableList.of(appName+".cfmockapps.io"), uris);

		doUnchangedAppRestartTest(app, deployedApp);
	}

	protected void doUnchangedAppRestartTest(CloudAppDashElement app, MockCFApplication deployedApp) throws Exception {
		//Try to restart app. Nothing should change because we haven't changed the manifest
		CFApplication appInfo = deployedApp.getBasicInfo();
		app.restart(RunState.RUNNING, ui);
		waitForJobsToComplete();
		waitForState(app, RunState.RUNNING, 10_000);

		CFApplication newAppInfo = deployedApp.getBasicInfo();
		assertSameAppState(appInfo, newAppInfo);
		//If no change was detected the manifest compare dialog shouldn't have popped.
		verify(ui, never()).openManifestDiffDialog(any());
	}

	private void assertSameAppState(CFApplication appInfo, CFApplication newAppInfo) {
		assertEquals(appInfoCompareString(appInfo), appInfoCompareString(newAppInfo));
	}

	private String appInfoCompareString(CFApplication app) {
		StringBuilder buf = new StringBuilder();
		buf.append("name = " + app.getName());
		buf.append("instances = " + app.getInstances());
		buf.append("runningInstances = " + app.getRunningInstances());
		buf.append("memory = " + app.getMemory());
		buf.append("memory = " + app.getMemory());
		buf.append("guid = " + app.getGuid());
		buf.append("services = " + app.getServices());
		buf.append("buildpack = " + app.getBuildpackUrl());
		buf.append("uris = " + app.getUris());
		buf.append("state = " + app.getState());
		buf.append("diskQuota = " + app.getDiskQuota());
		buf.append("timeout = " + app.getTimeout());
		buf.append("command = " + app.getCommand());
		buf.append("stack = " + app.getStack());
		return buf.toString();
	}


	@Test public void pushSimpleWithDefaultManualManifest() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);

		IProject project = projects.createBootProject("to-deploy", withStarters("actuator", "web"));

		final String appName = project.getName();

		harness.answerDeploymentPrompt(ui, (dialog) -> {
			dialog.okPressed();
		});

		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		waitForApps(model, appName);

		CloudAppDashElement app = model.getApplication(appName);

		waitForState(app, RunState.RUNNING, 10000);

		assertEquals((Integer)1, space.getPushCount(appName).getValue());
		assertNull(app.getDeploymentManifestFile());
		assertEquals(1024, (int) app.getMemory());
		assertEquals(appName, app.getName());

		MockCFApplication deployedApp = space.getApplication(appName);
		doUnchangedAppRestartTest(app, deployedApp);
	}

	@Ignore @Test public void warDeploy() throws Exception {
		MavenSpringBootProject.DUMP_MAVEN_OUTPUT = true;
		try {
			CFClientParams targetParams = CfTestTargetParams.fromEnv();
			MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
			CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);

			IProject project = projects.createBootProject("to-deploy-war",
					withStarters("actuator", "web"),
					withPackaging("war")
			);
			assertEquals("war", springBootCore.project(project).getPackaging());

			String appName = project.getName();
			harness.answerDeploymentPrompt(ui, (dialog) -> {
				dialog.okPressed();
			});

			model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
			waitForApps(model, appName);

			CloudAppDashElement app = model.getApplication(appName);
			waitForState(app, RunState.RUNNING, 10000);

			assertEquals((Integer)1, space.getPushCount(appName).getValue());
			assertNull(app.getDeploymentManifestFile());
			assertEquals(1024, (int) app.getMemory());
			assertEquals(appName, app.getName());

			File projectLocation = project.getLocation().toFile();
			File warFile = new File(projectLocation, "target/"+project.getName()+"-0.0.1-SNAPSHOT.war");
			assertTrue("war file not found: "+warFile, warFile.exists());
			assertDeployedBytes(warFile, space.getApplication(appName));
		} finally {
			MavenSpringBootProject.DUMP_MAVEN_OUTPUT = false;
		}
	}

	@Test public void stopCancelsDeploy() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);

		IProject project = projects.createBootProject("to-deploy", withStarters("actuator", "web"));

		final String appName = appHarness.randomAppName();

		clientFactory.setAppStartDelay(TimeUnit.MINUTES, 2);
		harness.answerDeploymentPrompt(ui, appName, appName);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
		waitForApps(model, appName);

		CloudAppDashElement app = model.getApplication(appName);

		waitForState(app, RunState.STARTING, 3000);

		ACondition.waitFor("stop hammering", 20000, () -> {
			app.stopAsync(ui);
			assertEquals(RunState.INACTIVE, app.getRunState());
		});

		//TODO: can we check that deployment related jobs are really canceled/finished somehow?
		//   can we check that they didn't pop errors?
	}



	@Test public void stopCancelsStart() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		IProject project = projects.createBootProject("to-deploy", withStarters("web", "actuator"));
		final String appName = "foo";
		space.defApp(appName);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName);
		CloudAppDashElement app = model.getApplication(appName);
		app.setProject(project);

		waitForApps(model, appName);

		clientFactory.setAppStartDelay(TimeUnit.MINUTES, 2);

		app.getBaseRunStateExp().addListener(new ValueListener<RunState>() {
			@Override
			public void gotValue(LiveExpression<RunState> exp, RunState value) {
				System.out.println("Runstate -> "+value);
			}
		});
		System.out.println("Restaring app...");
		app.restart(RunState.RUNNING, ui);
		waitForState(app, RunState.STARTING, 30000);

		System.out.println("Stopping app...");
		app.stopAsync(ui);

		waitForState(app, RunState.INACTIVE, 20000);
		System.out.println("Stopped!");
	}

	@Test public void stopCancelsRestartOnly() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		IProject project = projects.createProject("to-deploy");
		final String appName = "foo";
		space.defApp(appName);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName);
		CloudAppDashElement app = model.getApplication(appName);
		app.setProject(project);

		waitForApps(model, appName);

		clientFactory.setAppStartDelay(TimeUnit.MINUTES, 2);
		app.restartOnlyAsynch(ui, app.createCancelationToken());
		waitForState(app, RunState.STARTING, 3000);

		app.stopAsync(ui);
		waitForState(app, RunState.INACTIVE, 20000);
	}

	@Test public void acceptDeployOfExistingApp() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		IProject project = projects.createBootProject("to-deploy", withStarters("actuator", "web"));
		final String appName = "foo";
		MockCFApplication deployedApp = space.defApp(appName);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName);
		CloudAppDashElement app = model.getApplication(appName);
		app.setProject(null);
		assertNull(app.getProject());
		waitForState(app, RunState.INACTIVE, 3000);

		harness.answerDeploymentPrompt(ui, appName, appName);
		Mockito.doReturn(ManifestDiffDialogModel.Result.USE_MANIFEST).when(ui).confirmReplaceApp(any(), any(), any(), any());
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		System.out.println(app.getRunState());
		waitForJobsToComplete();
		System.out.println(app.getRunState());
		assertEquals(project, app.getProject());
		assertEquals(1, deployedApp.getPushCount());

		verify(ui).confirmReplaceApp(any(), any(), any(), any());
	}


	@Test public void cancelDeployOfExistingApp() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		IProject project = projects.createBootProject("to-deploy", withStarters("web", "actuator"));
		final String appName = "foo";
		MockCFApplication deployedApp = space.defApp(appName);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName);
		CloudAppDashElement app = model.getApplication(appName);
		app.setProject(null);
		assertNull(app.getProject());

		harness.answerDeploymentPrompt(ui, appName, appName);
		doReturn(ManifestDiffDialogModel.Result.CANCELED).when(ui).confirmReplaceApp(any(), any(), any(), any());
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		waitForJobsToComplete();
		assertNull(app.getProject()); // since op was canceled it should not have set the project on the app.
		assertEquals(0, deployedApp.getPushCount());							  // since op was canceled it should not have deployed the app.

		verify(ui).confirmReplaceApp(any(), any(), any(), any());
	}

	@Test public void manifestDiffDialogNotShownWhenNothingChanged() throws Exception {
		final String appName = "foo";

		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		IProject project = projects.createBootProject("to-deploy", withStarters("web", "actuator"));
		IFile manifest = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n"
		);
		harness.answerDeploymentPrompt(ui, manifest);
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
		waitForApps(model, appName);
		CloudAppDashElement app = model.getApplication(appName);
		waitForState(app, RunState.RUNNING, 10_000);
		assertEquals(1, app.getActualInstances());

//		deployedApp.scaleInstances(2); // change it 'externally'
		assertEquals(1, app.getActualInstances()); //The model doesn't know yet that it has changed!

//		harness.answerDeploymentPrompt(ui, appName, appName);
		app.restart(RunState.RUNNING, ui);
		waitForJobsToComplete();

		//If no change was detected the manifest compare dialog shouldn't have popped.
		verify(ui, never()).openManifestDiffDialog(any());
	}

	@Test public void manifestDiffDialogShownWhenInstancesChangedExternally() throws Exception {
		final String appName = "foo";

		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		IProject project = projects.createBootProject("to-deploy", withStarters("web", "actuator"));
		createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n"
		);
		MockCFApplication deployedApp = space.defApp(appName);
		deployedApp.start(CancelationTokens.NULL);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName);
		waitForJobsToComplete();

		CloudAppDashElement app = model.getApplication(appName);
		app.setDeploymentManifestFile(project.getFile("manifest.yml"));
		app.setProject(project);
		assertEquals(1, app.getActualInstances());

		deployedApp.scaleInstances(2); // change it 'externally'
		assertEquals(1, app.getActualInstances()); //The model doesn't know yet that it has changed!

		app.restart(RunState.RUNNING, ui);

		waitForJobsToComplete();

		//If the change was detected the deployment props dialog should have popped exactly once.
		verify(ui).openManifestDiffDialog(any());
	}

	@Test public void manifestDiffDialogChooseUseManfifest() throws Exception {
		//Setup initial state for our test
		final String appName = "foo";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		IProject project = projects.createBootProject("to-deploy", withStarters("web", "actuator"));
		IFile manifest = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n" +
				"  memory: 1111M\n"
		);

		harness.answerDeploymentPrompt(ui, manifest);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		waitForApps(model, appName);
		CloudAppDashElement app = model.getApplication(appName);
		waitForState(app, RunState.RUNNING, APP_DEPLOY_TIMEOUT);

		{
			MockCFApplication appInCloud = space.getApplication(appName);
			assertEquals(1111, appInCloud.getMemory());
			Mockito.reset(ui);

			//// real test begins here

			appInCloud.setMemory(2222);
		}

		harness.answerManifestDiffDialog(ui, (ManifestDiffDialogModel dialog) -> {
			//??? code to check what's in the dialog???
			return ManifestDiffDialogModel.Result.USE_MANIFEST;
		});

		app.restart(RunState.RUNNING, ui);

		waitForJobsToComplete();
		{
			MockCFApplication appInCloud = space.getApplication(appName);
			assertEquals(2, appInCloud.getPushCount());
			assertEquals(RunState.RUNNING, app.getRunState());
			assertEquals(1111, appInCloud.getMemory());
			assertEquals(1111, (int)app.getMemory());
		}
	}

	@Test public void manifestDiffDialogChooseForgetManfifest() throws Exception {
		//Setup initial state for our test
		final String appName = "foo";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		IProject project = projects.createBootProject("to-deploy", withStarters("web", "actuator"));
		IFile manifest = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n" +
				"  memory: 1111M\n"
		);

		harness.answerDeploymentPrompt(ui, manifest);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		waitForApps(model, appName);
		CloudAppDashElement app = model.getApplication(appName);
		waitForState(app, RunState.RUNNING, APP_DEPLOY_TIMEOUT);

		MockCFApplication appInCloud = space.getApplication(appName);
		assertEquals(1111, appInCloud.getMemory());
		Mockito.reset(ui);

		//// real test begins here

		appInCloud.setMemory(2222);

		harness.answerManifestDiffDialog(ui, (ManifestDiffDialogModel dialog) -> {
			//??? code to check what's in the dialog???
			return ManifestDiffDialogModel.Result.FORGET_MANIFEST;
		});

		app.restart(RunState.RUNNING, ui);

		waitForJobsToComplete();

		assertEquals(2, appInCloud.getPushCount());
		assertEquals(RunState.RUNNING, app.getRunState());
		assertEquals(2222, appInCloud.getMemory());
		assertEquals(2222, (int)app.getMemory());
	}

	@Ignore @Test public void testDeployManifestWithAbsolutePathAttribute() throws Exception {
		final String appName = "foo";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		IProject project = projects.createProject("to-deploy");

		File zipFile = getTestZip("testapp");

		IFile manifestFile = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: foo\n" +
				"  path: "+zipFile.getAbsolutePath() + "\n" +
				"  buildpack: staticfile_buildpack"
		);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);

		harness.answerDeploymentPrompt(ui, manifestFile);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		waitForApps(model, "foo");

		CloudAppDashElement app = model.getApplication("foo");
		waitForState(app, RunState.RUNNING, APP_DELETE_TIMEOUT);

		assertEquals(project, app.getProject());

		assertEquals("some content here\n", space.getApplication(appName).getFileContents("test.txt"));

		verify(ui).promptApplicationDeploymentProperties(any());
		verifyNoMoreInteractions(ui);
	}


	@Ignore @Test public void testDeployManifestWithRelativePathAttribute() throws Exception {
		final String appName = "foo";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		IProject project = projects.createProject("to-deploy");

		File zipFile = getTestZip("testapp");
		project.getFolder("zips").create(true, true, new NullProgressMonitor());
		project.getFolder("manifests").create(true, true, new NullProgressMonitor());
		createFile(project, "zips/testapp.zip", zipFile);

		IFile manifestFile = createFile(project, "manifests/manifest.yml",
				"applications:\n" +
				"- name: foo\n" +
				"  path: ../zips/testapp.zip\n" +
				"  buildpack: staticfile_buildpack"
		);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);

		harness.answerDeploymentPrompt(ui, manifestFile);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		waitForApps(model, "foo");

		CloudAppDashElement app = model.getApplication("foo");
		waitForState(app, RunState.RUNNING, APP_DEPLOY_TIMEOUT);

		assertEquals(project, app.getProject());

		assertEquals("some content here\n", space.getApplication(appName).getFileContents("test.txt"));

		verify(ui).promptApplicationDeploymentProperties(any());
		verifyNoMoreInteractions(ui);
	}

	@Ignore @Test public void testDeployManifestWithoutPathAttribute() throws Exception {
		String appName = "foo";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);

		IProject project = projects.createBootWebProject("empty-web-app");
		IFile manifestFile = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n"
		);
		File referenceJar = BootJarPackagingTest.packageAsJar(project, ui);

		harness.answerDeploymentPrompt(ui, manifestFile);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
		waitForApps(model, appName);

		CloudAppDashElement app = model.getApplication(appName);
		waitForState(app, RunState.RUNNING, APP_DEPLOY_TIMEOUT);

		System.out.println("platform location = '"+Platform.getLocation()+"'");
		assertDeployedBytes(referenceJar, space.getApplication(appName));
	}

	@Test public void testSelectManifestActionEnablement() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		IProject project1 = projects.createProject("pr1");
		IProject project2 = projects.createProject("pr2");

		final String appName1 = "app1";
		final String appName2 = "app2";

		MockCFApplication cfApp1 = space.defApp(appName1);
		MockCFApplication cfApp2 = space.defApp(appName2);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName1, appName2);

		CloudAppDashElement app1 = model.getApplication(appName1);
		CloudAppDashElement app2 = model.getApplication(appName2);

		app1.setProject(project1);
		app2.setProject(project2);

		IAction action = actions.getSelectManifestAction();

		assertTrue(harness.selection.getElements().isEmpty());
		assertFalse(action.isEnabled());

		harness.selection.setElements(ImmutableSet.of(app1));
		assertNotNull(app1.getProject());
		assertTrue(action.isEnabled());

		harness.selection.setElements(ImmutableSet.of(app1, app2));
		assertFalse(action.isEnabled());

		app1.setProject(null);
		harness.selection.setElements(ImmutableSet.of(app1));
		assertFalse(action.isEnabled());

		harness.selection.setElements(ImmutableSet.of(app2));
		assertTrue(action.isEnabled());
		action.run();

	}

	@Test public void testSelectManifestAction() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		IProject project = projects.createProject("pr");

		final String appName = "app";

		IFile manifestFile = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n"
		);

		space.defApp(appName);

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName);

		CloudAppDashElement app = model.getApplication(appName);
		app.setProject(project);

		harness.selection.setElements(ImmutableSet.of(app));

		harness.answerDeploymentPrompt(ui, manifestFile);

		assertNull(app.getDeploymentManifestFile());
		actions.getSelectManifestAction().run();
		waitForJobsToComplete();
		assertEquals(manifestFile, app.getDeploymentManifestFile());

		verify(ui).promptApplicationDeploymentProperties(any());
		verifyNoMoreInteractions(ui);
	}

	@Test public void disconnectTarget() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		String appName = "someApp";
		space.defApp(appName);
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName);
		assertEquals(1, clientFactory.instanceCount());

		harness.sectionSelection.setValue(model);
		IAction disconnectAction = actions.getToggleTargetConnectionAction();
		assertTrue(disconnectAction.isEnabled());
		disconnectAction.run();
		waitForApps(model);
		assertFalse(model.isConnected());
		assertEquals(RefreshState.READY, model.getRefreshState());
		assertEquals(0, clientFactory.instanceCount());
	}

	@Test public void updateTargetPasswordInvalid() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		String appName = "someApp";
		space.defApp(appName);
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);

		waitForApps(model, appName);

		harness.sectionSelection.setValue(model);
		IAction updatePassword = actions.getUpdatePasswordAction();
		assertTrue(updatePassword.isEnabled());

		harness.answerPasswordPrompt(ui, (d) -> {
			d.getPasswordVar().setValue(targetParams.getCredentials().getSecret());
			d.validateCredentials().block();
			d.performOk();
		});

		updatePassword.run();

		waitForJobsToComplete();

		assertNotNull(model.getApplication(appName));
		assertTrue(model.isConnected());
		assertEquals(RefreshState.READY, model.getRefreshState());

		// Clear out any mocks on the ui object set above
		reset(ui);

		CompletableFuture<ValidationResult> passwordValidation = new CompletableFuture<>();

		harness.answerPasswordPrompt(ui, (d) -> {
			d.getPasswordVar().setValue("wrong password");
			ReactorUtils.completeWith(passwordValidation, d.validateCredentials());
			//d.performOk(); //shouldn't perform ok because we are expecting passwordValidation to fail
		});

		updatePassword.run();

		ValidationResult validationResult = passwordValidation.get();
		assertEquals(IStatus.ERROR, validationResult.status);
		assertContains("Invalid credentials", validationResult.msg);

	}

	@Test public void pushWithHealthCheckNone() throws Exception {
		doPushWithHealthCheckType("none", "none");
	}

	@Test public void pushWithHealthCheckDefault() throws Exception {
		doPushWithHealthCheckType(null, "port");
	}

	@Test public void pushWithHealthCheckPort() throws Exception {
		doPushWithHealthCheckType("port", "port");
	}

	private void doPushWithHealthCheckType(String specified, String expected) throws Exception {
		String appName = "someApp";
		final IProject project = projects.createBootProject(appName, withStarters("actuator", "web"));
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		CloudFoundryBootDashModel model = harness.createCfTarget(targetParams);

		harness.answerDeploymentPrompt(ui, (dialog) -> {
			dialog.setManifestType(ManifestType.MANUAL);
			dialog.setManualManifest(
					"applications:\n" +
					"- name: "+appName+"\n" +
					(specified==null?"":"  health-check-type: "+specified+"\n")
			);
			dialog.okPressed();
		});
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
		waitForApps(model, appName);
		waitForState(model.getApplication(appName), RunState.RUNNING, 4000);
		waitForJobsToComplete();

		assertEquals(expected,space.getApplication(appName).getHealthCheckType());
		ACondition.waitFor("hcType in model", 3000, () -> {
			assertEquals(expected, model.getApplication(appName).getHealthCheck());
		});

		MockCFApplication deployedApp = space.getApplication(appName);
		doUnchangedAppRestartTest(model.getApplication(appName), deployedApp);
	}

	@Test public void updateTargetSsoAndStoreNothing() throws Exception {
		String appName = "someApp";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		space.defApp(appName);
		CloudFoundryBootDashModel model = harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_TOKEN);
		waitForApps(model, appName);

		harness.sectionSelection.setValue(model);
		IAction updatePassword = actions.getUpdatePasswordAction();
		assertTrue(updatePassword.isEnabled());

		// Clear out any mocks on the ui object
		reset(ui);

		String newToken=clientFactory.getSsoToken();
		harness.answerPasswordPrompt(ui, (d) -> {
			d.getMethodVar().setValue(LoginMethod.TEMPORARY_CODE);
			d.getPasswordVar().setValue(newToken);
			d.getStoreVar().setValue(StoreCredentialsMode.STORE_NOTHING);
			d.validateCredentials().block();
			d.performOk();
		});

		updatePassword.run();

		waitForJobsToComplete();

		assertTrue(model.isConnected());
		assertNotNull(model.getApplication(appName));
		assertEquals(RefreshState.READY, model.getRefreshState());

		{
			assertNull(harness.getCredentialsStore().getCredentials(harness.secureStoreKey(model)));
			assertNull(harness.getPrivateStore().get(harness.privateStoreKey(model)));
		}

		actions.getToggleTargetConnectionAction().run();

		waitForJobsToComplete();
		assertFalse(model.isConnected());

		// Clear out any mocks on the ui object to get the right count below
		reset(ui);

		actions.getToggleTargetConnectionAction().run();
		waitForJobsToComplete();

		assertFalse(model.isConnected());
		assertEquals(RefreshState.READY, model.getRefreshState());
		assertNull(model.getApplication(appName));

		verify(ui).openPasswordDialog(any());
		verifyNoMoreInteractions(ui);
	}

	@Test public void updateTargetSsoAndStorePassword() throws Exception {
		String appName = "someApp";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		space.defApp(appName);
		CloudFoundryBootDashModel model = harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_TOKEN);
		waitForApps(model, appName);

		harness.sectionSelection.setValue(model);
		IAction updatePassword = actions.getUpdatePasswordAction();
		assertTrue(updatePassword.isEnabled());

		// Clear out any mocks on the ui object
		reset(ui);

		LiveVariable<ValidationResult> capturedStoreValidator = new LiveVariable<>(null);

		String newToken=clientFactory.getSsoToken();
		harness.answerPasswordPrompt(ui, (d) -> {
			d.getMethodVar().setValue(LoginMethod.TEMPORARY_CODE);
			d.getPasswordVar().setValue(newToken);
			d.getStoreVar().setValue(StoreCredentialsMode.STORE_PASSWORD);
			capturedStoreValidator.setValue(d.getStoreValidator().getValue());
			d.validateCredentials().block();
			d.performOk();
		});

		updatePassword.run();

		waitForJobsToComplete();

		assertContains("'Store Password' is useless for a 'Temporary Code'", capturedStoreValidator.getValue().msg);

		assertTrue(model.isConnected());
		assertNotNull(model.getApplication(appName));
		assertEquals(RefreshState.READY, model.getRefreshState());
		//store password is ignored and treated as 'STORE_NOTHING'
		assertEquals(StoreCredentialsMode.STORE_NOTHING, model.getRunTarget().getTargetProperties().getStoreCredentials());
		{
			assertNull(harness.getCredentialsStore().getCredentials(harness.secureStoreKey(model)));
			assertNull(harness.getPrivateStore().get(harness.privateStoreKey(model)));
		}

		actions.getToggleTargetConnectionAction().run();

		waitForJobsToComplete();
		assertFalse(model.isConnected());

		// Clear out any mocks on the ui object to get the right count below
		reset(ui);

		actions.getToggleTargetConnectionAction().run();
		waitForJobsToComplete();

		assertFalse(model.isConnected());
		assertEquals(RefreshState.READY, model.getRefreshState());
		assertNull(model.getApplication(appName));

		verify(ui).openPasswordDialog(any());
		verifyNoMoreInteractions(ui);
	}

	@Test public void updateTargetSsoAndStoreToken() throws Exception {
		String appName = "someApp";
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		space.defApp(appName);
		CloudFoundryBootDashModel model = harness.createCfTarget(targetParams, StoreCredentialsMode.STORE_PASSWORD);
		waitForApps(model, appName);

		harness.sectionSelection.setValue(model);
		IAction updatePassword = actions.getUpdatePasswordAction();
		assertTrue(updatePassword.isEnabled());

		// Clear out any mocks on the ui object
		reset(ui);

		String newToken=clientFactory.getSsoToken();
		harness.answerPasswordPrompt(ui, (d) -> {
			d.getMethodVar().setValue(LoginMethod.TEMPORARY_CODE);
			d.getPasswordVar().setValue(newToken);
			d.getStoreVar().setValue(StoreCredentialsMode.STORE_TOKEN);
			d.validateCredentials().block();
			d.performOk();
		});

		updatePassword.run();

		waitForJobsToComplete();

		assertTrue(model.isConnected());
		assertNotNull(model.getApplication(appName));
		assertEquals(RefreshState.READY, model.getRefreshState());

		assertEquals(StoreCredentialsMode.STORE_TOKEN, model.getRunTarget().getTargetProperties().getStoreCredentials());
		{
			assertNull(harness.getCredentialsStore().getCredentials(harness.secureStoreKey(model)));
			assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, harness.getPrivateStore().get(harness.privateStoreKey(model)));
		}

		actions.getToggleTargetConnectionAction().run();

		waitForJobsToComplete();
		assertFalse(model.isConnected());

		// Clear out any mocks on the ui object to get the right count below
		reset(ui);

		actions.getToggleTargetConnectionAction().run();
		waitForJobsToComplete();

		assertTrue(model.isConnected());
		assertEquals(RefreshState.READY, model.getRefreshState());
		assertNotNull(model.getApplication(appName));

		verifyZeroInteractions(ui); //should have used stored token so no pw dialog!
	}

	@Test public void updateTargetPasswordAndStoreNothing() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		String appName = "someApp";
		space.defApp(appName);
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName);

		harness.sectionSelection.setValue(model);
		IAction updatePassword = actions.getUpdatePasswordAction();
		assertTrue(updatePassword.isEnabled());

		// Clear out any mocks on the ui object
		reset(ui);

		harness.answerPasswordPrompt(ui, (d) -> {
			d.getPasswordVar().setValue(targetParams.getCredentials().getSecret());
			d.getStoreVar().setValue(StoreCredentialsMode.STORE_NOTHING);
			d.validateCredentials().block();
			d.performOk();
		});

		updatePassword.run();

		waitForJobsToComplete();

		assertTrue(model.isConnected());
		assertNotNull(model.getApplication(appName));
		assertEquals(RefreshState.READY, model.getRefreshState());

		{
			assertNull(harness.getCredentialsStore().getCredentials(harness.secureStoreKey(model)));
			assertNull(harness.getPrivateStore().get(harness.privateStoreKey(model)));
		}

		actions.getToggleTargetConnectionAction().run();

		waitForJobsToComplete();
		assertFalse(model.isConnected());

		// Clear out any mocks on the ui object to get the right count below
		reset(ui);

		actions.getToggleTargetConnectionAction().run();
		waitForJobsToComplete();

		assertFalse(model.isConnected());
		assertEquals(RefreshState.READY, model.getRefreshState());
		assertNull(model.getApplication(appName));

		verify(ui).openPasswordDialog(any());
		verifyNoMoreInteractions(ui);
	}

	@Test public void updateTargetPasswordAndStorePassword() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		String appName = "someApp";
		space.defApp(appName);
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		waitForApps(model, appName);

		harness.sectionSelection.setValue(model);
		IAction updatePassword = actions.getUpdatePasswordAction();
		assertTrue(updatePassword.isEnabled());

		// Clear out any mocks on the ui object
		reset(ui);

		harness.answerPasswordPrompt(ui, (d) -> {
			d.getPasswordVar().setValue(targetParams.getCredentials().getSecret());
			d.getStoreVar().setValue(StoreCredentialsMode.STORE_PASSWORD);
			d.validateCredentials().block();
			d.performOk();
		});

		updatePassword.run();

		waitForJobsToComplete();

		actions.getToggleTargetConnectionAction().run();

		waitForJobsToComplete();
		assertFalse(model.isConnected());

		// Clear out any mocks on the ui object to get the right count below
		reset(ui);

		actions.getToggleTargetConnectionAction().run();
		waitForJobsToComplete();

		assertTrue(model.isConnected());
		assertEquals(RefreshState.READY, model.getRefreshState());
		assertNotNull(model.getApplication(appName));

		verifyNoMoreInteractions(ui);
	}

	@Test public void updateTargetPasswordAndStoreToken() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		String appName = "someApp";
		space.defApp(appName);
		CloudFoundryBootDashModel target =  harness.createCfTarget(targetParams);
		waitForApps(target, appName);

		harness.sectionSelection.setValue(target);
		IAction updatePassword = actions.getUpdatePasswordAction();
		assertTrue(updatePassword.isEnabled());

		// Clear out any mocks on the ui object
		reset(ui);

		harness.answerPasswordPrompt(ui, (d) -> {
			d.getPasswordVar().setValue(targetParams.getCredentials().getSecret());
			d.getStoreVar().setValue(StoreCredentialsMode.STORE_TOKEN);
			d.validateCredentials().block();
			d.performOk();
		});

		updatePassword.run();

		waitForJobsToComplete();

		actions.getToggleTargetConnectionAction().run();

		waitForJobsToComplete();
		assertFalse(target.isConnected());

		// Clear out any mocks on the ui object to get the right count below
		reset(ui);

		actions.getToggleTargetConnectionAction().run();
		waitForJobsToComplete();

		assertTrue(target.isConnected());
		CFCredentials creds = target.getRunTarget().getTargetProperties().getCredentials();
		assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, creds.getSecret());
		assertEquals(CFCredentialType.REFRESH_TOKEN, creds.getType());
		assertEquals(MockCloudFoundryClientFactory.FAKE_REFRESH_TOKEN, harness.getPrivateStore().get(harness.privateStoreKey(target)));
		assertNull(harness.getCredentialsStore().getCredentials(harness.secureStoreKey(target)));
		assertEquals(RefreshState.READY, target.getRefreshState());
		assertNotNull(target.getApplication(appName));

		verifyNoMoreInteractions(ui);

		clientFactory.changeRefrestToken("another-1");
		clientFactory.changeRefrestToken("another-2");
		ACondition.waitFor("changed stored token", 300, () -> {
			assertEquals("another-2", getStoredToken(target));
		});
	}


	@Test public void apiVersionCheck() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());

		clientFactory.setApiVersion("1.1.0");
		clientFactory.setSupportedApiVersion("1.1.1");

		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		ACondition.waitFor("connected to target", 10_000, () -> model.isConnected());

		waitForJobsToComplete();

		assertTrue(model.getRefreshState().isWarning());
		String msg = model.getRefreshState().getMessage();
		assertContains("may not work as expected", msg);
		assertContains("1.1.1", msg);
		assertContains("1.1.0", msg);
	}

	@Test public void clientDisposedWhenTargetRemoved() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		space.defApp("bonkers");
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		ACondition.waitFor("connected to target", 10_000, () -> {
			assertTrue(model.isConnected());
			assertEquals(RefreshState.READY, model.getRefreshState());
		});
		waitForApps(model, "bonkers");

		assertEquals(1, clientFactory.instanceCount());

		harness.sectionSelection.setValue(model);
		when(ui.confirmOperation(contains("Deleting"), any()))
			.thenReturn(true);
		actions.getRemoveRunTargetAction().run();

		ACondition.waitFor("target removed", 10_000, () -> {
			harness.model.getSectionModels().getValue().isEmpty();
		});
		assertEquals(0, clientFactory.instanceCount());
	}

	@Test public void oldClientDisposedWhenClientCredentialsUpdated() throws Exception {
		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		MockCFSpace space = clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		space.defApp("bonkers");
		CloudFoundryBootDashModel model =  harness.createCfTarget(targetParams);
		ACondition.waitFor("connected to target", 10_000, () -> {
			assertTrue(model.isConnected());
			assertEquals(RefreshState.READY, model.getRefreshState());
		});
		waitForApps(model, "bonkers");
		assertEquals(1, clientFactory.instanceCount());

		clientFactory.setPassword("something-else");

		harness.sectionSelection.setValue(model);
		harness.answerPasswordPrompt(ui, (PasswordDialogModel passwordDialog) -> {
			passwordDialog.getPasswordVar().setValue("something-else");
			assertEquals(ValidationResult.OK, passwordDialog.validateCredentials().block());
			passwordDialog.performOk();
		});
		harness.sectionSelection.setValue(model);
		UpdatePasswordAction updatePW = actions.getUpdatePasswordAction();
		updatePW.run();
		updatePW.waitFor();

		ACondition.waitFor("connected to target", 10_000, () -> {
			assertTrue(model.isConnected());
			assertEquals(RefreshState.READY, model.getRefreshState());
		});
		assertEquals(1, clientFactory.instanceCount());
	}

	@Test public void openAppWithPathInBrowser() throws Exception {
		final String appName = "to-deploy";
		final String appPath = "/the-path";
		String projectName = "to-deploy";
		final String host = "foo-host";

		CFClientParams targetParams = CfTestTargetParams.fromEnv();
		clientFactory.defSpace(targetParams.getOrgName(), targetParams.getSpaceName());
		final IProject project = projects.createBootProject(projectName, withStarters("actuator", "web"));
		harness.createCfTarget(targetParams);
		final CloudFoundryBootDashModel target = harness.getCfTargetModel();
		IFile manifest = createFile(project, "manifest.yml",
				"applications:\n" +
				"- name: "+appName+"\n" +
				"  routes:\n" +
				"  - route: "+host+".cfmockapps.io"+appPath
		);
		harness.answerDeploymentPrompt(ui, manifest);
		target.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);
		waitForApps(target, appName);

		CloudAppDashElement app = target.getApplication(appName);
		ACondition.waitFor("uri update", 5_000, () -> {
			assertEquals("foo-host.cfmockapps.io", app.getLiveHost());
			assertEquals("http://foo-host.cfmockapps.io/the-path", app.getUrl());
		});

		app.setDefaultRequestMappingPath("/hello");
		ACondition.waitFor("uri update", 5_000, () -> {
			assertEquals("foo-host.cfmockapps.io", app.getLiveHost());
			assertEquals("http://foo-host.cfmockapps.io/the-path/hello", app.getUrl());
		});

		app.setDefaultRequestMappingPath("/");
		ACondition.waitFor("uri update", 5_000, () -> {
			assertEquals("foo-host.cfmockapps.io", app.getLiveHost());
			assertEquals("http://foo-host.cfmockapps.io/the-path/", app.getUrl());
		});

		app.setDefaultRequestMappingPath("hello");
		ACondition.waitFor("uri update", 5_000, () -> {
			assertEquals("foo-host.cfmockapps.io", app.getLiveHost());
			assertEquals("http://foo-host.cfmockapps.io/the-path/hello", app.getUrl());
		});

		app.setDefaultRequestMappingPath("");
		ACondition.waitFor("uri update", 5_000, () -> {
			assertEquals("foo-host.cfmockapps.io", app.getLiveHost());
			assertEquals("http://foo-host.cfmockapps.io/the-path", app.getUrl());
		});

	}

	///////////////////////////////////////////////////////////////////////////////////////////////

	//Stuff below is 'cruft' intended to make the tests above more readable. Maybe this code could be
	// moved to some kind of 'harness' (if there is a case where it can be reused).

	private void assertDeployedBytes(File referenceJar, MockCFApplication app) throws IOException {
		try (InputStream actualBits = app.getBits()) {
			try (InputStream expectedBits = new BufferedInputStream(new FileInputStream(referenceJar))) {
				ZipDiff zipDiff = new ZipDiff(expectedBits);
				try {
					zipDiff.assertEqual(actualBits);
				} catch (Throwable e) {
					System.out.println("Failed: "+ExceptionUtil.getMessage(e));
					saveArtefacts(referenceJar, app);
					throw e;
				}
			}
		}
	}

	private void saveArtefacts(File referenceJar, MockCFApplication app) throws IOException {
		System.out.println("Trying to save jars...");
		File targetDir = getSaveDir();
		System.out.println("targetDir = "+targetDir);
		if (targetDir!=null) {
			int id = uniqueId++;
			File referenceJarCopy = new File(targetDir, "deployed-reference-"+id+".jar");
			File faultyJarCopy = new File(targetDir, "deployed-faulty-"+id+".jar");
			FileUtils.copyFile(referenceJar, referenceJarCopy);
			System.out.println("Reference jar saved: "+referenceJarCopy);
			IOUtil.pipe(app.getBits(), faultyJarCopy);
			System.out.println("Faulty jar saved: "+faultyJarCopy);
		}
	}

	private static int uniqueId = 0;

	private File getSaveDir() {
		IPath targetDirPath = Platform.getLocation();
		while (targetDirPath.segmentCount()>0 && !targetDirPath.lastSegment().equals("target")) {
			targetDirPath = targetDirPath.removeLastSegments(1);
		}
		if (targetDirPath.segmentCount()>0) {
			return targetDirPath.toFile();
		}
		return new File(System.getProperty("user.home"));
	}

	private File getTestZip(String fileName) {
		File sourceWorkspace = new File(
				StsTestUtil.getSourceWorkspacePath("org.springframework.ide.eclipse.boot.dash.test"));
		File file = new File(sourceWorkspace, fileName + ".zip");
		Assert.isTrue(file.exists(), ""+ file);
		return file;
	}

	private void assertAppToProjectBinding(CloudFoundryBootDashModel target, IProject project, String appName) throws Exception {
		CloudAppDashElement appByProject = getApplication(target, project);
		CloudAppDashElement appByName = target.getApplication(appName);
		assertNotNull(appByProject);
		assertEquals(appByProject, appByName);
	}

	private CloudAppDashElement getApplication(CloudFoundryBootDashModel model, IProject project) {
		for (CloudAppDashElement app : model.getApplicationValues()) {
			IProject p = app.getProject();
			if (project.equals(p)) {
				return app;
			}
		}
		return null;
	}

	protected CloudAppDashElement deployApp(final CloudFoundryBootDashModel model, final String appName, IProject project)
			throws Exception {
		harness.answerDeploymentPrompt(ui, appName, appName);
		model.performDeployment(ImmutableSet.of(project), ui, RunState.RUNNING);

		waitForApps(model, appName);

		new ACondition("wait for app '"+ appName +"'to be RUNNING", 30000) { //why so long? JDT searching for main type.
			public boolean test() throws Exception {
				CloudAppDashElement element = model.getApplication(appName);
				assertEquals(RunState.RUNNING, element.getRunState());
				return true;
			}
		};
		return model.getApplication(appName);
	}

	protected void waitForApps(final CloudFoundryBootDashModel target, final String... names) throws Exception {
		new ACondition("wait for apps to appear", 120_000) {
			@Override
			public boolean test() throws Exception {
				ImmutableSet<String> appNames = getNames(target.getApplications().getValues());
				assertEquals(ImmutableSet.copyOf(names), appNames);
				return true;
			}
		};
	}

	protected void waitForServices(final CloudFoundryBootDashModel target, final String... names) throws Exception {
		new ACondition("wait for services to appear", 3000) {
			@Override
			public boolean test() throws Exception {
				ImmutableSet<String> serviceNames = getNames(target.getServices().getValues());
				assertEquals(ImmutableSet.copyOf(names), serviceNames);
				return true;
			}
		};
	}

	protected void waitForElements(final CloudFoundryBootDashModel target, final String... names) throws Exception {
		new ACondition("wait for elements to appear", 3000) {
			@Override
			public boolean test() throws Exception {
				ImmutableSet<String> elements = getNames(target.getElements().getValues());
				assertEquals(ImmutableSet.copyOf(names), elements);
				return true;
			}
		};
	}

	private Answer<Void> restoreDefaultTemplate() {
		return new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				EditTemplateDialogModel dialog = (EditTemplateDialogModel) invocation.getArguments()[0];
				dialog.restoreDefaultsHandler.call();
				dialog.performOk();
				return null;
			}
		};
	}

	/**
	 * Create a mockito {@link Answer} that interacts with EditTemplateDialog by setting the template value and then
	 * clicking the OK button.
	 */
	private Answer<Void> editSetTemplate(final String newText) {
		return new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				EditTemplateDialogModel dialog = (EditTemplateDialogModel) invocation.getArguments()[0];
				dialog.template.setValue(newText);
				dialog.performOk();
				return null;
			}
		};
	}

	private void assertSorted(ImmutableList<IAction> actions) {
		String[] actionNames = new String[actions.size()];
		for (int i = 0; i < actionNames.length; i++) {
			actionNames[i] = actions.get(i).getText();
		}

		String actual = StringUtils.arrayToDelimitedString(actionNames, "\n");

		Arrays.sort(actionNames);
		String expected = StringUtils.arrayToDelimitedString(actionNames, "\n");

		assertEquals(expected, actual);
	}

	private <T extends BootDashElement> void debugListener(final String name, ObservableSet<T> set) {
		set.addListener(new ValueListener<ImmutableSet<T>>() {

			@Override
			public void gotValue(LiveExpression<ImmutableSet<T>> exp, ImmutableSet<T> value) {
				StringBuilder elements = new StringBuilder();
				for (BootDashElement e : exp.getValue()) {
					elements.append(e.getName());
					elements.append(" ");
				}
				System.out.println(name+" -> "+elements);
			}
		});
	}

	private void assertNames(ArrayList<BootDashElement> elements, String... expectNames) {
		String[] actualNames = new String[elements.size()];
		for (int i = 0; i < actualNames.length; i++) {
			actualNames[i] = elements.get(i).getName();
		}
		assertArrayEquals(expectNames, actualNames);
	}

	private ImmutableSet<String> getNames(ImmutableSet<? extends BootDashElement> values) {
		Builder<String> builder = ImmutableSet.builder();
		for (BootDashElement e : values) {
			builder.add(e.getName());
		}
		return builder.build();
	}

	public static void waitForState(final BootDashElement element, final RunState state, long timeOut) throws Exception {
		new ACondition("Wait for state "+state, timeOut) {
			@Override
			public boolean test() throws Exception {
				assertEquals(state, element.getRunState());
				return true;
			}
		};
	}


	///////////////////////////////////////////////////////////////////////////////////


}
