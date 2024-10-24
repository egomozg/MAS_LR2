package ru.mpei;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.core.AID;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FunctionAgent extends Agent {
	private boolean receivedQueue = false;
	private String functionType;                          // Тип функции: "agent1", "agent2", "agent3"
	private double delta = 1.0;                           // Начальное значение delta
	private double x;                                     // Текущее значение X
	private double precision = 0.01;                      // Точность
	private boolean isInitiator = false;                  // Флаг инициатора
	private List<String> otherAgents = new ArrayList<>(); // Список других агентов

	@Override
	protected void setup() {
		// Получаем тип функции из аргументов
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			functionType = (String) args[0];
		} else {
			System.out.println("Не указан тип функции для агента " + getLocalName());
			doDelete();
			return;
		}
		System.out.println("Агент " + getLocalName() + " запускается. Тип функции: " + functionType);

		// Инициализируем список известных агентов
		otherAgents.add("agent1");
		otherAgents.add("agent2");
		otherAgents.add("agent3");
		otherAgents.remove(getLocalName()); // Исключаем себя
		System.out.println(getLocalName() + " список otherAgents при старте: " + otherAgents);

		// Добавляем поведения
		addBehaviour(new CalculationRequestReceiverBehaviour());
		addBehaviour(new QueueReceiverBehaviour());

		// Если агент является инициатором
		if (getLocalName().equals("agent1")) {
			isInitiator = true;
			addBehaviour(new FunctionCalculationInitiatorBehaviour());
		}
	}

	@Override
	protected void takeDown() {
		System.out.println("Агент " + getLocalName() + " завершает работу.");
	}

	private class CalculationRequestReceiverBehaviour extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("calculate")
			);
			ACLMessage msg = receive(mt);
			if (msg != null) {
				// Получен запрос на расчет функции
					double[] xValues = (double[]) msg.getContentObject();
					double[] yValues = new double[xValues.length];
					for (int i = 0; i < xValues.length; i++) {
						yValues[i] = calculateFunction(xValues[i]);
					}
					// Отправляем ответ
					ACLMessage reply = msg.createReply();
					reply.setPerformative(ACLMessage.INFORM);
					reply.setContentObject(yValues);
					send(reply);
					System.out.println(getLocalName() + " отправил результаты вычислений агенту " + msg.getSender().getLocalName());
			} else {
				block();
			}
		}
	}

	private class FunctionCalculationInitiatorBehaviour extends Behaviour {
		private int step = 0;
		private int repliesCount = 0;
		private double[] yValues = new double[3];
		private double[] sumY = new double[3];
		private double currentBestSumY = Double.NEGATIVE_INFINITY;
		private double[] xValues = new double[3];
		private boolean finished = false;

		@Override
		public void action() {
			switch (step) {
				case 0:
					if (!receivedQueue) {
						// Если агент не получил очередь, выбираем начальную точку случайно
						x = new Random().nextDouble() * 10 - 5; // Диапазон от -5 до 5
						delta = 1.0; // Инициализируем delta
						System.out.println(getLocalName() + " выбрал начальную точку X = " + x);
					} else {
						System.out.println(getLocalName() + " продолжает с X = " + x + ", delta = " + delta);
					}

					xValues[0] = x - delta;
					xValues[1] = x;
					xValues[2] = x + delta;

					// Инициализируем sumY и repliesCount
					sumY = new double[3];
					repliesCount = 0;

					// Отправляем запросы другим агентам
					ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
					for (String agentName : otherAgents) {
						request.addReceiver(new AID(agentName, AID.ISLOCALNAME));
					}
					request.setConversationId("calculate");
					try {
						request.setContentObject(xValues);
					} catch (IOException e) {
						e.printStackTrace();
					}
					send(request);
					System.out.println(getLocalName() + " отправил запрос другим агентам на вычисление функций.");

					// Переходим к следующему шагу
					step = 1;
					break;

				case 1:
					// Ждем ответы от других агентов
					MessageTemplate mt = MessageTemplate.and(
							MessageTemplate.MatchPerformative(ACLMessage.INFORM),
							MessageTemplate.MatchConversationId("calculate")
					);
					ACLMessage reply = receive(mt);
					if (reply != null) {
						double[] receivedYValues = null;
						try {
							receivedYValues = (double[]) reply.getContentObject();
						} catch (UnreadableException e) {
							throw new RuntimeException(e);
						}
						for (int i = 0; i < 3; i++) {
								sumY[i] += receivedYValues[i];
							}
							repliesCount++;
							System.out.println(getLocalName() + " получил ответ от " + reply.getSender().getLocalName());
							if (repliesCount == otherAgents.size()) {
								// Все ответы получены
								// Добавляем свои значения
								for (int i = 0; i < 3; i++) {
									yValues[i] = calculateFunction(xValues[i]);
									sumY[i] += yValues[i];
								}
								// Ищем максимум суммарной функции
								int maxIndex = 0;
								for (int i = 0; i < 3; i++) {
									if (sumY[i] > currentBestSumY) {
										currentBestSumY = sumY[i];
										maxIndex = i;
									}
								}
								if (xValues[maxIndex] != x) {
									// Новая точка
									x = xValues[maxIndex];
									System.out.println(getLocalName() + " нашел новую лучшую точку X = " + x + " с суммой Y = " + currentBestSumY);
									// Передаем очередь следующему агенту
									addBehaviour(new QueueSenderBehaviour(x, delta));
									isInitiator = false;
									finished = true;
								} else {
									// Уменьшаем delta
									delta /= 2;
									System.out.println(getLocalName() + " уменьшает delta до " + delta);
									if (delta < precision) {
										// Завершаем работу
										System.out.println("Найденный максимум: X = " + x + ", сумма Y = " + currentBestSumY);
										doDelete();
										finished = true;
									} else {
										// Повторяем шаги
										step = 0;
									}
								}
							}
					} else {
						block();
					}
					break;
			}
		}

		@Override
		public boolean done() {
			return finished;
		}
	}

	private class QueueSenderBehaviour extends OneShotBehaviour {
		private double xToSend;
		private double deltaToSend;

		public QueueSenderBehaviour(double x, double delta) {
			this.xToSend = x;
			this.deltaToSend = delta;
		}

		@Override
		public void action() {
				String nextAgent = otherAgents.get(new Random().nextInt(otherAgents.size()));
				ACLMessage passMsg = new ACLMessage(ACLMessage.INFORM);
				passMsg.addReceiver(new AID(nextAgent, AID.ISLOCALNAME));
				passMsg.setConversationId("pass");
				double[] packet = {xToSend, deltaToSend};
			try {
				passMsg.setContentObject(packet);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			send(passMsg);
				System.out.println(getLocalName() + " передает очередь агенту " + nextAgent);
		}
	}

	private class QueueReceiverBehaviour extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchConversationId("pass");
			ACLMessage msg = receive(mt);
			if (msg != null) {
				// Получена передача очереди
				try {
					double[] receivedValues = (double[]) msg.getContentObject();
					x = receivedValues[0];
					delta = receivedValues[1];
				} catch (UnreadableException e) {
					throw new RuntimeException(e);
				}
				String previousInitiator = msg.getSender().getLocalName();
				System.out.println(getLocalName() + " получил очередь. Новая X = " + x + ", delta = " + delta);

				// Агент становится инициатором
				isInitiator = true;
				receivedQueue = true; // Устанавливаем флаг, что очередь получена
				addBehaviour(new FunctionCalculationInitiatorBehaviour());
			} else {
				block();
			}
		}
	}

	/**
	 * Метод для вычисления функции
	 */
	private double calculateFunction(double x) {
		return switch (functionType) {
			case "agent1" -> -0.5 * x * x - 4;
			case "agent2" -> Math.pow(2, -0.1 * x);
			case "agent3" -> Math.cos(x);
			default -> 0;
		};
	}
}
