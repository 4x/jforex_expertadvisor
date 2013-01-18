package sampleStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class OrderTest implements IStrategy {

	/** Log */
	private static final Logger LOGGER = LoggerFactory.getLogger(OrderTest.class);

	private IEngine engine;
	private IConsole console;
	private IHistory history;
	private IContext context;
	private IIndicators indicators;

	// private IOrder order = null;

	@Override
	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.context = context;
		this.indicators = context.getIndicators();
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		// // 各通貨ペアのティック情報を受信した際に呼ばれます。
		// LOGGER.info("onTick");
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {

		// 1個でも注文があれば何もしない
		if (1 == engine.getOrders().size()) {
			return;
		}

		String label = "Buy" + System.currentTimeMillis();

		IOrder orderByLabel = engine.getOrder(label);

		if (null != orderByLabel) {
			return;
		}

		IOrder order = engine.submitOrder(
				label,
				Instrument.USDJPY,// instrument 通貨ペア
				OrderCommand.BUY,
				0.01,// amount 単位
				askBar.getClose(),// 金額
				0,// slippage スリッページ
				askBar.getClose() - 1,// 損切り
				askBar.getClose() + 0.05// 利食い
				);

		 // while (order.getState() == IOrder.State.CREATED || order.getState() == IOrder.State.OPENED) {
		// order.waitForUpdate(2, TimeUnit.SECONDS);
		// }

		 if (order.getState() == IOrder.State.FILLED) {
			System.out.println(order.getOpenPrice() + " : " + order.getStopLossPrice() + " : " + order.getTakeProfitPrice());
		}

		 if (order.getState() == IOrder.State.CANCELED) {
			order = null;
		}
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		// 注文の約定や決済、その他各種メッセージを受信した際に呼ばれます。

		IOrder order = message.getOrder();

		switch (order.getState()) {
		case CLOSED:
			LOGGER.info("onMessage : " + message + " Close:" + order.getClosePrice());
			break;
		default:
			LOGGER.info("onMessage : " + message);
			break;
		}
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
		// // ストラテジーを停止した際に呼ばれます。
		// LOGGER.info("onStop");
	}

	@Override
	public void onStop() throws JFException {
		// // 各通貨ペアのティック情報を受信した際に呼ばれます。
		// LOGGER.info("onTick");
	}

	public static void main(String[] args) {
		int rsi = 31;
		if (rsi <= 30) {
			System.out.println(1);
		}
	}
}
