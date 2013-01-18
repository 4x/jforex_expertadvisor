package myjforex.strategy;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import myjforex.DateUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IOrder.State;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

/**
 * ストラテジー
 */
public class MyStrategy implements IStrategy {

	/** CSV区切り文字 */
	private static final String SEPARATOR = ",";

	/** 日付フォーマットラベル用 */
	private static SimpleDateFormat LABEL_DATEFORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");

	/** Log */
	private static final Logger LOGGER = LoggerFactory.getLogger(MyStrategy.class);

	@Configurable("通貨ペア")
	private Instrument instrument = Instrument.USDJPY;

	@Configurable("単位")
	private double amount = 0.01;

	@Configurable("スリッページ")
	private double slippage = 0;

	@Configurable("損切り値")
	private double stopLossPrice = 0;

	@Configurable("利食い値")
	private double takeProfitPrice = 0.05;

	@Configurable("時間枠")
	private Period period = Period.THIRTY_MINS;

	@Configurable("ビッド or アスク RSIを取得する時にどっちのレートを使用するかという意味で売り買いには関係ない")
	private OfferSide offerSide = OfferSide.BID;

	@Configurable("使用レート")
	private AppliedPrice appliedPrice = AppliedPrice.CLOSE;

	/** 期間14 */
	@Configurable("期間")
	private int rsiTimePeriod = 14;

	@Configurable("買われ過ぎ")
	private int rsiUpperLine = 70;

	@Configurable("売られ過ぎ")
	private int rsiLowerLine = 30;

	/** Engine */
	private IEngine engine;

	/** Indicators */
	private IIndicators indicators;

	/** Acount */
	private IAccount acount;

	/** 処理中の注文 */
	private IOrder aliveOrder = null;

	/** 結果ファイル出力 */
	private PrintWriter printWriter;

	/**
	 * RSIの許容範囲を超えた場合にTRUE, それ以外はFALSE<br />
	 * FALSEの時に仕掛けていい
	 */
	private boolean rsizone;

	/* (非 Javadoc)
	 *
	 * @see com.dukascopy.api.IStrategy#onStart(com.dukascopy.api.IContext) */
	public void onStart(IContext context) throws JFException {

		// おきまり
		this.engine = context.getEngine();
		this.indicators = context.getIndicators();
		this.acount = context.getAccount();
		// 初期値はfalse
		this.rsizone = false;

		try {
			// 売買結果csv (純正のレポートがヘボすぎるので)
			File file = new File("MyStrategy.csv");
			printWriter = new PrintWriter(new BufferedWriter(new FileWriter(file)));
			// 結果出力ファイルのヘッダーを作成
			StringBuffer print = new StringBuffer();
			print.append("Type").append(SEPARATOR)
					.append("Label").append(SEPARATOR)
					.append("Amount").append(SEPARATOR)
					.append("Direction").append(SEPARATOR)
					.append("OpenPrice").append(SEPARATOR)
					.append("Profit/Loss in pips").append(SEPARATOR)
					.append("OpenDate").append(SEPARATOR)
					.append("CloseDate").append(SEPARATOR)
					.append("Comments").append(SEPARATOR)
					.append("A_Global (アカウントがグローバル・アカウントの場合にtrue)").append(SEPARATOR)
					.append("A_CreditLine (取引可能額。値は約5秒間隔で更新されている。)").append(SEPARATOR)
					.append("A_Currency (アカウントの通貨設定)").append(SEPARATOR)
					.append("A_Equity (純資産額。値は約5秒間隔で更新されている。)").append(SEPARATOR)
					.append("A_Leverage (レバレッジ設定)").append(SEPARATOR)
					.append("A_MarginCutLevel (マージンカットレベル。このレベルを超過するとマージンカット(ロスカット)。)").append(SEPARATOR)
					.append("A_OverWeekEndLeverage (ウィークエンド・レバレッジ・レベル。このレベルを超過するとマージンカット(ロスカット)。)").append(SEPARATOR)
					.append("A_UseOfLeverage (証拠金使用率。値は約5秒間隔で更新されている。)").append(SEPARATOR)
					.append("A_Balance");
			printWriter.println(print.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* (非 Javadoc)
	 *
	 * @see com.dukascopy.api.IStrategy#onAccount(com.dukascopy.api.IAccount) */
	public void onAccount(IAccount account) throws JFException {
		// アカウント情報が更新された際に呼ばれます。
		// LOGGER.trace("onAccount : " + account.getAccountId());
	}

	private void aliveOrderInit() {
		this.aliveOrder = null;
		this.rsizone = false;
	}

	/* (非 Javadoc)
	 *
	 * @see com.dukascopy.api.IStrategy#onMessage(com.dukascopy.api.IMessage) */
	public void onMessage(IMessage message) throws JFException {
		// 注文の約定や決済、その他各種メッセージを受信した際に呼ばれます。

		// Messageに含まれる注文情報
		IOrder order = message.getOrder();

		// LOG文字列
		StringBuffer log = new StringBuffer();
		log.append(message.getType() + " ");
		switch (message.getType()) {
		case CALENDAR:
			log.append("(ライブ経済指標)");
			break;
		case CONNECTION_STATUS:
			log.append("(接続状況が変化した場合に送られます)");
			break;
		case NEWS:
			log.append("(マーケット・ニュース)");
			break;
		case NOTIFICATION:
			log.append("(サーバからの通知や回線の切断等のシステムイベント)");
			break;
		case ORDER_CHANGED_OK:
			log.append("(注文の変更が完了した後に送られます)");
			break;
		case ORDER_CHANGED_REJECTED:
			log.append("(注文の変更がサーバ側で拒否された場合に送られます)");
			break;
		case ORDER_CLOSE_OK:
			log.append("(注文の決済が完了した後に送られます)");
			break;
		case ORDER_CLOSE_REJECTED:
			log.append("(注文の決済がサーバ側で拒否された場合に送られます)");
			break;
		case ORDER_FILL_OK:
			log.append("(注文が約定した後に送られます)");
			break;
		case ORDER_FILL_REJECTED:
			log.append("(注文の約定がサーバ側で拒否された場合に送られます)");
			aliveOrderInit();
			break;
		case ORDER_SUBMIT_OK:
			log.append("(注文がサーバ側で受け付けた後に送られます)");
			break;
		case ORDER_SUBMIT_REJECTED:
			log.append("(注文がサーバ側で受け付けられなかった場合に送られます)");
			aliveOrderInit();
			break;
		case ORDERS_MERGE_OK:
			log.append("(注文のマージが完了した後に送られます)");
			break;
		case ORDERS_MERGE_REJECTED:
			log.append("(注文のマージがサーバ側で拒否された場合に送られます)");
			break;
		default:
			log.append("(こんなのしらない)");
			break;
		}

		if (order != null) {
			// 結果ファイルに出力
			log.append(SEPARATOR)
					.append(order.getLabel()).append(SEPARATOR)
					.append(order.getAmount()).append(SEPARATOR)
					.append(order.getOrderCommand()).append(SEPARATOR)
					.append(order.getOpenPrice()).append(SEPARATOR)
					.append(order.getProfitLossInPips()).append(SEPARATOR)
					.append(DateUtils.longToStringZeroForEmpty(order.getFillTime())).append(SEPARATOR)
					.append(DateUtils.longToStringZeroForEmpty(order.getCloseTime())).append(SEPARATOR)
					.append(order.getComment()).append(SEPARATOR)
					.append(acount.isGlobal()).append(SEPARATOR)
					.append(acount.getCreditLine()).append(SEPARATOR)
					.append(acount.getCurrency()).append(SEPARATOR)
					.append(acount.getEquity()).append(SEPARATOR)
					.append(acount.getLeverage()).append(SEPARATOR)
					.append(acount.getMarginCutLevel()).append(SEPARATOR)
					.append(acount.getOverWeekEndLeverage()).append(SEPARATOR)
					.append(acount.getUseOfLeverage()).append(SEPARATOR)
					.append(acount.getBalance());
			printWriter.println(log.toString());
			printWriter.flush();
		}
	}

	/* (非 Javadoc)
	 *
	 * @see com.dukascopy.api.IStrategy#onStop() */
	public void onStop() throws JFException {
		// ストラテジーを停止した際に呼ばれます。
		LOGGER.info("onStop");
		printWriter.close();
	}

	/* (非 Javadoc)
	 *
	 * @see com.dukascopy.api.IStrategy#onTick(com.dukascopy.api.Instrument, com.dukascopy.api.ITick) */
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		// 各通貨ペアのティック情報を受信した際に呼ばれます。
		// LOGGER.info("onTick");
	}

	/**
	 * 各期間及び通貨ペアのバー情報を受信した際に呼ばれます。<br />
	 * 10秒単位でCallされる
	 *
	 * @param instrument 通貨ペア
	 * @param period 期間
	 * @param askBar 買い情報
	 * @param bidBar 売り情報
	 * @see com.dukascopy.api.IStrategy#onBar(com.dukascopy.api.Instrument, com.dukascopy.api.Period, com.dukascopy.api.IBar, com.dukascopy.api.IBar)
	 */
	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (instrument != this.instrument || period != this.period) {
			// 対象の通貨ではない、かつ、対象の時間間隔でない場合は何もしない
			return;
		}

		// 既に注文があるときは何もしない
		if (this.aliveOrder != null && this.aliveOrder.getState() != State.CLOSED) {
			this.aliveOrder.waitForUpdate(2, TimeUnit.SECONDS);
			return;
		}

		// 現在のRSI (フラットフィルターを適用しないとRSIが0や、100になる)
		// http://kakakufx.com/mk2/systrade/indicators.html
		double rsi = indicators.rsi(instrument,
				period,
				this.offerSide,
				this.appliedPrice,
				this.rsiTimePeriod,
				Filter.ALL_FLATS, // 全てのフラットバーを除外
				1, // 1本前
				askBar.getTime(), // 現在日付
				0)[0];

		if (rsizone) {
			if (rsiLowerLine < rsi && rsi < rsiUpperLine) {
				rsizone = false;
			} else {
				return;
			}
		}

		if (rsi <= rsiLowerLine) {
			// 今のRSIも前回のRSIも30を下回ってる場合
			// [/] は使えないみたい
			String label = "Buy_" + LABEL_DATEFORMAT.format(new Date(System.currentTimeMillis()));
			submitOrder(label, OrderCommand.BUY, askBar, rsi);
			// 注文したので仕掛けちゃだめ設定
			rsizone = true;
		}
		if (rsiUpperLine <= rsi) {
			// 今のRSIも前回のRSIも70を上回ってる場合
			// [/] は使えないみたい
			String label = "Sell_" + LABEL_DATEFORMAT.format(new Date(System.currentTimeMillis()));
			submitOrder(label, OrderCommand.SELL, bidBar, rsi);
			// 注文したので仕掛けちゃだめ設定
			rsizone = true;
		}
	}

	/**
	 * 注文する
	 * 
	 * @param label
	 * @param orderCmd
	 * @param bar
	 * @param rsi
	 * @throws JFException
	 */
	private void submitOrder(String label, OrderCommand orderCmd, IBar bar, double rsi) throws JFException {
		// 約定したら、損切りと利食いを設定
		// 値段 = 約定値段
		double price = bar.getClose();
		// 損切り
		double stoploss = 0;
		// 利食い
		double takeprofit = 0;
		switch (orderCmd) {
		// 買いの場合、損切り = 値段 - ストップロス設定値, 利食い = 値段 + 利食い設定値
		case BUY:
			stoploss = (0 == this.stopLossPrice ? 0 : price - this.stopLossPrice);
			takeprofit = (0 == this.takeProfitPrice ? 0 : price + this.takeProfitPrice);
			break;
		case SELL:
			stoploss = (0 == this.stopLossPrice ? 0 : price + this.stopLossPrice);
			takeprofit = (0 == this.takeProfitPrice ? 0 : price - this.takeProfitPrice);
			break;
		default:
			break;
		}

		// 注文
		this.aliveOrder = engine.submitOrder(
				label,
				this.instrument,
				orderCmd,
				this.amount,
				price,
				this.slippage,
				stoploss,
				takeprofit,
				0,
				DateUtils.longToString(bar.getTime()) + " RSI : " + rsi);

		// ORDER_SUBMIT_REJECTED時にaliveOrderをNULLにしているのでNULL判定してる
		while (this.aliveOrder != null &&
				(this.aliveOrder.getState() == IOrder.State.CREATED ||
						this.aliveOrder.getState() == IOrder.State.OPENED)) {
			this.aliveOrder.waitForUpdate(2, TimeUnit.SECONDS);
		}
		if (this.aliveOrder != null &&
				(this.aliveOrder.getState() == IOrder.State.CANCELED)) {
			this.aliveOrder = null;
		}
	}

}