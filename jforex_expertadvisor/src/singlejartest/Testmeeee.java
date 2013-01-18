package singlejartest;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Set;

import myjforex.Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sampleStrategy.CopyOfRSI;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.ITesterClient.InterpolationMethod;
import com.dukascopy.api.system.JFAuthenticationException;
import com.dukascopy.api.system.JFVersionException;
import com.dukascopy.api.system.TesterFactory;

public class Testmeeee {

	/** Log */
	private static final Logger LOGGER = LoggerFactory.getLogger(Testmeeee.class);

	/** reportを作成するファイル名(無いとダメ) */
	private static String filepath = "report.html";

	public static void main(String[] args) throws JFAuthenticationException, JFVersionException, Exception {

		final ITesterClient client = TesterFactory.getDefaultInstance();

		client.setSystemListener(new ISystemListener() {
			@Override
			public void onStart(long processId) {
				LOGGER.info("onStart");
			}

			@Override
			public void onStop(long processId) {
				LOGGER.info("Strategy stopped: " + processId);
				File reportFile = new File(filepath);
				try {
					client.createReport(processId, reportFile);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}
				if (client.getStartedStrategies().size() == 0) {
					System.exit(0);
				}
			}

			@Override
			public void onConnect() {
				LOGGER.info("onConnect");
			}

			@Override
			public void onDisconnect() {
				LOGGER.info("onDisconnect");
			}
		});

		client.connect(Constants.JNLP_URL, Constants.USER_NAME, Constants.PASSWORD);

		LOGGER.info("setSubscribedInstruments");
		Set<Instrument> instruments = new HashSet<Instrument>();
		instruments.add(Instrument.USDJPY);
		client.setSubscribedInstruments(instruments);

		// setting initial deposit
		LOGGER.info("setInitialDeposit");
		// 日本円で5万円
		client.setInitialDeposit(Instrument.USDJPY.getSecondaryCurrency(), 500000);

		// タイムゾーン
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss SSS");
		Long from = dateFormat.parse("2010/01/01 00:00:00 000").getTime();
		Long to = dateFormat.parse("2011/06/01 00:00:00 000").getTime();

		LOGGER.info("setDataInterval");
		client.setDataInterval(
				Period.THIRTY_MINS,
				OfferSide.ASK,
				InterpolationMethod.CLOSE_TICK,
				from,
				to);

		// // 過去データを先に全てキャッシュに保存+アルファする
		// LOGGER.info("downloadData");
		// LoadingProgressListener loadingProgressListener = new LoadingProgressListener() {
		// @Override
		// public void dataLoaded(long startTime, long endTime, long currentTime, String information) {
		// LOGGER.info("dataLoaded: " + startTime + ":" + endTime + ":" + currentTime + ":" + information);
		// }
		// @Override
		// public void loadingFinished(boolean allDataLoaded, long startTime, long endTime, long currentTime) {
		// LOGGER.info("loadingFinished: " + allDataLoaded + "_" + startTime + "_" + endTime + "_" + currentTime);
		// }
		// @Override
		// public boolean stopJob() {
		// return false;
		// }
		// };
		// Future<?> future = client.downloadData(loadingProgressListener);
		// future.get();

		LOGGER.info("startStrategy");
		// client.startStrategy(new RSI());
		client.startStrategy(new CopyOfRSI());
		// client.startStrategy(new OrderTest());
	}
}
