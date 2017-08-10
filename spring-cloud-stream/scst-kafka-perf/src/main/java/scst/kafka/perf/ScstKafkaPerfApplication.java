package scst.kafka.perf;


import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@SpringBootApplication
@EnableScheduling
@EnableBinding(Sink.class)
public class ScstKafkaPerfApplication implements CommandLineRunner {

	@Value("${num.messages:1000}")
	private int numMessages;

	@Value("${print.on.every:10000}")
	private int printStatusCount;

	private int prevN = 0;

	public static void main(String[] args) {
		SpringApplication.run(ScstKafkaPerfApplication.class, args).close();
	}

	private final CountDownLatch latch = new CountDownLatch(1);

	@Override
	public void run(String... arg0) throws Exception {
		latch.await();
		Thread.sleep(3000);
	}

	@Bean
	@ConditionalOnProperty("non.declarative")
	public NonDeclarativeStreamListener nonDeclarative() {
		return new NonDeclarativeStreamListener(latch, numMessages,
				printStatusCount);
	}

	@Bean
	@ConditionalOnProperty("declarative")
	public DeclarativeStreamListener declarative() {
		return new DeclarativeStreamListener(latch, numMessages,
				printStatusCount);
	}

	@Component
	@ConditionalOnProperty({"multi.consumer.non.declarative"})
	public class FooTask {

		@Autowired
		NonDeclarativeStreamListener nonDeclarativeStreamListener;

		@Scheduled(fixedRate = 1000, initialDelay = 4000)
		public void foo() {
			if (nonDeclarativeStreamListener.currentCount == prevN) {
				if (nonDeclarativeStreamListener.watch.isRunning()) {
					nonDeclarativeStreamListener.watch.stop();
					System.out.println(nonDeclarativeStreamListener.watch.toString() + "... "
							+ (nonDeclarativeStreamListener.currentCount / ((float) nonDeclarativeStreamListener.watch.getTotalTimeMillis() / (float) 1000))
							+ " messages per second");
					latch.countDown();
				}
			}
			prevN = nonDeclarativeStreamListener.currentCount;
		}

	}

	@Component
	@ConditionalOnProperty({"multi.consumer.declarative"})
	public class BarTask {

		@Autowired
		DeclarativeStreamListener declarativeStreamListener;

		@Scheduled(fixedRate = 1000, initialDelay = 4000)
		public void bar() {
			if (declarativeStreamListener.currentCount == prevN) {
				if (declarativeStreamListener.watch.isRunning()) {
					declarativeStreamListener.watch.stop();
					System.out.println(declarativeStreamListener.watch.toString() + "... "
							+ (declarativeStreamListener.currentCount / ((float) declarativeStreamListener.watch.getTotalTimeMillis() / (float) 1000))
							+ " messages per second");
					latch.countDown();
				}
			}
			prevN = declarativeStreamListener.currentCount;
		}
	}

	public static class NonDeclarativeStreamListener {

		private final CountDownLatch latch;

		private StopWatch watch;

		private int currentCount;

		private int numMessages;

		private int printStatusCount;

		public NonDeclarativeStreamListener(CountDownLatch latch, int numMessages, int printStatusCount) {
			this.latch = latch;
			this.numMessages = numMessages;
			this.printStatusCount = printStatusCount;
			watch = new StopWatch("Receive " + numMessages + " Stream");
		}

		@StreamListener(Sink.INPUT)
		public void listen(byte[] bytes) {
			if (currentCount++ == 0) {
				this.watch.start();
			}
			else if (currentCount == numMessages) {
				this.watch.stop();
				System.out.println(this.watch.toString() + "... "
						+ (numMessages / ((float) watch.getTotalTimeMillis() / (float) 1000))
						+ " messages per second");
				latch.countDown();
			}
			if (currentCount % printStatusCount == 0) {
				System.out.println("Current count: " + currentCount + "   " + LocalDateTime.now());
			}

		}
	}

	public static class DeclarativeStreamListener {

		private final CountDownLatch latch;

		private StopWatch watch = new StopWatch("Receive 30M Stream");

		private int currentCount;

		private int numMessages;

		private int printStatusCount;

		public DeclarativeStreamListener(CountDownLatch latch, int numMessages, int printStatusCount) {
			this.latch = latch;
			this.numMessages = numMessages;
			this.printStatusCount = printStatusCount;
			watch = new StopWatch("Receive " + numMessages + " Stream");
		}

		@StreamListener
		public void listen(@Input(Sink.INPUT) SubscribableChannel messageChannel) {
			messageChannel.subscribe(message -> {
				if (currentCount++ == 0) {
					DeclarativeStreamListener.this.watch.start();
				}

				else if (currentCount == numMessages) {
					DeclarativeStreamListener.this.watch.stop();
					System.out.println(DeclarativeStreamListener.this.watch.toString() + "... "
							+ (numMessages / ((float) watch.getTotalTimeMillis() / (float) 1000))
							+ " messages per second");
					latch.countDown();
				}

				if (currentCount % printStatusCount == 0) {
					System.out.println("Current count: " + currentCount + "   " + LocalDateTime.now());
				}
			});
		}
	}

}

