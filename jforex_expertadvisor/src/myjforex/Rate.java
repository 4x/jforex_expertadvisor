package myjforex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.ITesterClient.InterpolationMethod;
import com.dukascopy.api.system.JFAuthenticationException;
import com.dukascopy.api.system.JFVersionException;
import com.dukascopy.api.system.TesterFactory;

/**
 * レート取得テスト
 */
public class Rate {

	/** FROM */
	private static final String FROM = "2010/09/21 00:00:00 000";
	/** TO */
	private static final String TO = "2010/09/23 00:00:00 000";

	private static final String TAB = "\t";

	/** Log */
	private static final Logger LOGGER = LoggerFactory.getLogger(Rate.class);

	/**
	 * @param args
	 * @throws JFAuthenticationException
	 * @throws JFVersionException
	 * @throws Exception
	 */
	public static void main(String[] args) throws JFAuthenticationException, JFVersionException, Exception {

		// 結果出力ファイル
		File file = new File("rate.txt");
		final PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(file)));

		final ITesterClient client = TesterFactory.getDefaultInstance();

		client.setSystemListener(new ISystemListener() {
			@Override
			public void onStart(long processId) {
				LOGGER.info("onStart");
			}

			@Override
			public void onStop(long processId) {
				LOGGER.info("Strategy stopped: " + processId);
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

		// 接続
		client.connect(Constants.JNLP_URL, Constants.USER_NAME, Constants.PASSWORD);

		// 通貨ペア設定
		LOGGER.info("setSubscribedInstruments");
		Set<Instrument> instruments = new HashSet<Instrument>();
		instruments.add(Instrument.USDJPY);
		client.setSubscribedInstruments(instruments);

		// 初期金額設定 (ここでは日本円で5万円
		LOGGER.info("setInitialDeposit");
		client.setInitialDeposit(Instrument.USDJPY.getSecondaryCurrency(), 50000);

		// 期間設定
		LOGGER.info("setDataInterval");
		client.setDataInterval(
				Period.THIRTY_MINS,
				OfferSide.ASK,
				InterpolationMethod.CLOSE_TICK,
				DateUtils.StringToLong(FROM),
				DateUtils.StringToLong(TO));

		LOGGER.info("startStrategy");
		printWriter.println("TIME" + TAB + "RATE" + TAB + "RSI");
		client.startStrategy(new IStrategy() {

			private IIndicators indicators;

			@Override
			public void onTick(Instrument instrument, ITick tick) throws JFException {
			}

			@Override
			public void onStop() throws JFException {
				printWriter.close();
			}

			@Override
			public void onStart(IContext context) throws JFException {
				this.indicators = context.getIndicators();
			}

			@Override
			public void onMessage(IMessage message) throws JFException {
			}

			@Override
			public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {

				if (instrument != Instrument.USDJPY || period != Period.THIRTY_MINS) {
					return;
				}

				// RSI
				Double rsi = indicators.rsi(
						instrument,
						period,
						OfferSide.ASK,
						AppliedPrice.CLOSE,
						14,
						1);
				printWriter.println(DateUtils.longToString(askBar.getTime()) + TAB + askBar.getClose() + TAB + rsi);
			}

			@Override
			public void onAccount(IAccount account) throws JFException {
			}
		});
	}
}
