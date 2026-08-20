package test.com.nihility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nihility.InternalMessenger;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class InternalMessengerTest {
    private final static String INTENT_ACTION = "test";
    private final IntentFilter intentFilter = new IntentFilter(INTENT_ACTION);
    private final Context applicationContext = ApplicationProvider.getApplicationContext();
    InternalMessenger sender = new InternalMessenger(applicationContext);
    InternalMessenger receiver = new InternalMessenger(applicationContext);

    @Before
    public void setUp() {
        receiver.register(intentFilter);
    }

    @After
    public void tearDown() {
        receiver.close();
        sender.close();
    }

    @Test
    public void receiveFromAnotherMessenger() throws InterruptedException {
        CountDownLatch doneSignal = new CountDownLatch(1);
        receiver.addListener(intent -> doneSignal.countDown());

        sender.send(new Intent(INTENT_ACTION));

        assertTrue(doneSignal.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void allListenersAreCalled() throws InterruptedException {
        int listeners = 10;
        CountDownLatch doneSignal = new CountDownLatch(listeners);
        for (int i = 0; i < listeners; i++) {
            receiver.addListener(intent -> doneSignal.countDown());
        }

        sender.send(new Intent(INTENT_ACTION));

        assertTrue(doneSignal.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void receiveByMultipleMessengers() throws InterruptedException {
        int messengerCount = 10;
        CountDownLatch doneSignal = new CountDownLatch(messengerCount);
        InternalMessenger[] messengers = new InternalMessenger[messengerCount];
        for (int i = 0; i < messengerCount; i++) {
            InternalMessenger messenger = new InternalMessenger(applicationContext);
            messengers[i] = messenger;
            messenger.register(intentFilter);
            messenger.addListener(intent -> doneSignal.countDown());
        }

        sender.send(new Intent(INTENT_ACTION));

        assertTrue(doneSignal.await(1, TimeUnit.SECONDS));
        for (InternalMessenger messenger : messengers) {
            messenger.close();
        }
    }

    @Test
    public void repeatedRegisterDoesNotDeliverDuplicates() throws InterruptedException {
        receiver.register(intentFilter);
        CountDownLatch doneSignal = new CountDownLatch(1);
        AtomicInteger deliveries = new AtomicInteger();
        receiver.addListener(intent -> {
            deliveries.incrementAndGet();
            doneSignal.countDown();
        });

        sender.send(new Intent(INTENT_ACTION));

        assertTrue(doneSignal.await(1, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(1, deliveries.get());
    }

    @Test
    public void distinctRegistrationsRemainActive() throws InterruptedException {
        String secondAction = "test.second";
        receiver.register(new IntentFilter(secondAction));
        CountDownLatch doneSignal = new CountDownLatch(2);
        receiver.addListener(intent -> doneSignal.countDown());

        sender.send(new Intent(INTENT_ACTION));
        sender.send(new Intent(secondAction));

        assertTrue(doneSignal.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void closeIsIdempotentAndStopsDelivery() throws InterruptedException {
        AtomicInteger deliveries = new AtomicInteger();
        receiver.addListener(intent -> deliveries.incrementAndGet());

        receiver.close();
        receiver.close();
        sender.send(new Intent(INTENT_ACTION));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertEquals(0, deliveries.get());
    }

    @Test
    public void closedMessengerCannotBeRegisteredAgain() throws InterruptedException {
        AtomicInteger deliveries = new AtomicInteger();
        receiver.addListener(intent -> deliveries.incrementAndGet());
        receiver.close();
        receiver.register(intentFilter);
        receiver.addListener(intent -> deliveries.incrementAndGet());

        sender.send(new Intent(INTENT_ACTION));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertEquals(0, deliveries.get());
    }

    @Test
    public void listenerCanMutateListenersDuringDelivery() throws InterruptedException {
        CountDownLatch doneSignal = new CountDownLatch(1);
        receiver.addListener(intent -> {
            receiver.addListener(ignored -> { });
            doneSignal.countDown();
        });

        sender.send(new Intent(INTENT_ACTION));

        assertTrue(doneSignal.await(1, TimeUnit.SECONDS));
    }
}
