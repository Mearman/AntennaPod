package de.danoeh.antennapod.net.sync.wearinterface;

import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeClient;
import com.google.android.gms.wearable.Wearable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class WearConnectionUtilsTest {
    private Context context;
    private MockedStatic<Wearable> wearable;
    private MockedStatic<Tasks> tasks;
    private CapabilityClient capabilityClient;
    private NodeClient nodeClient;
    private Task<CapabilityInfo> capabilityTask;
    private Task<List<Node>> nodesTask;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        capabilityClient = mock(CapabilityClient.class);
        nodeClient = mock(NodeClient.class);
        capabilityTask = mock(Task.class);
        nodesTask = mock(Task.class);
        when(capabilityClient.getCapability(any(), anyInt())).thenReturn(capabilityTask);
        when(nodeClient.getConnectedNodes()).thenReturn(nodesTask);
        wearable = mockStatic(Wearable.class);
        wearable.when(() -> Wearable.getCapabilityClient(context)).thenReturn(capabilityClient);
        wearable.when(() -> Wearable.getNodeClient(context)).thenReturn(nodeClient);
        tasks = mockStatic(Tasks.class);
    }

    @After
    public void tearDown() {
        wearable.close();
        tasks.close();
    }

    private static Node node(String name) {
        Node node = mock(Node.class);
        when(node.getDisplayName()).thenReturn(name);
        return node;
    }

    private void capabilityNodes(Set<Node> nodes) {
        CapabilityInfo info = mock(CapabilityInfo.class);
        when(info.getNodes()).thenReturn(nodes);
        tasks.when(() -> Tasks.await(capabilityTask)).thenReturn(info);
    }

    @Test
    public void phoneIsSupportedWhenAReachableNodeOffersTheCapability() {
        capabilityNodes(Collections.singleton(node("Phone")));

        assertTrue(WearConnectionUtils.isPhoneSupported(context));
        verify(capabilityClient).getCapability(eq("antennapod_phone_v1"), eq(CapabilityClient.FILTER_REACHABLE));
    }

    @Test
    public void phoneIsNotSupportedWhenNoNodeOffersTheCapability() {
        capabilityNodes(Collections.emptySet());

        assertFalse(WearConnectionUtils.isPhoneSupported(context));
    }

    @Test
    public void phoneIsNotSupportedWhenCapabilityLookupFails() {
        tasks.when(() -> Tasks.await(capabilityTask)).thenThrow(new ExecutionException(new RuntimeException("failed")));

        assertFalse(WearConnectionUtils.isPhoneSupported(context));
    }

    @Test
    public void phoneIsNotSupportedWhenCapabilityLookupIsInterruptedAndInterruptFlagIsKept() {
        tasks.when(() -> Tasks.await(capabilityTask)).thenThrow(new InterruptedException("interrupted"));

        try {
            assertFalse(WearConnectionUtils.isPhoneSupported(context));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void connectedNodeNameIsTheDisplayNameOfTheFirstNode() {
        List<Node> nodes = Arrays.asList(node("First"), node("Second"));
        tasks.when(() -> Tasks.await(nodesTask)).thenReturn(nodes);

        assertEquals("First", WearConnectionUtils.getConnectedNodeName(context));
    }

    @Test
    public void connectedNodeNameIsEmptyWithoutConnectedNodes() {
        tasks.when(() -> Tasks.await(nodesTask)).thenReturn(Collections.emptyList());

        assertEquals("", WearConnectionUtils.getConnectedNodeName(context));
    }

    @Test
    public void connectedNodeNameIsEmptyWhenLookupFails() {
        tasks.when(() -> Tasks.await(nodesTask)).thenThrow(new ExecutionException(new RuntimeException("failed")));

        assertEquals("", WearConnectionUtils.getConnectedNodeName(context));
    }

    @Test
    public void connectedNodeNameIsEmptyWhenLookupIsInterruptedAndInterruptFlagIsKept() {
        tasks.when(() -> Tasks.await(nodesTask)).thenThrow(new InterruptedException("interrupted"));

        try {
            assertEquals("", WearConnectionUtils.getConnectedNodeName(context));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
