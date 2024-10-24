package ru.mpei;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class MainContainer{
	public static void main(String[] args) {
		Runtime rt = Runtime.instance();

		Profile profile = new ProfileImpl();

		profile.setParameter(Profile.GUI, "true");
		AgentContainer mainContainer = rt.createMainContainer(profile);

		try {
			// Создание и запуск агентов
			AgentController agent1 = mainContainer.createNewAgent("agent1", "ru.mpei.FunctionAgent", new Object[]{"agent1"});
			AgentController agent2 = mainContainer.createNewAgent("agent2", "ru.mpei.FunctionAgent", new Object[]{"agent2"});
			AgentController agent3 = mainContainer.createNewAgent("agent3", "ru.mpei.FunctionAgent", new Object[]{"agent3"});

			agent1.start();
			agent2.start();
			agent3.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}