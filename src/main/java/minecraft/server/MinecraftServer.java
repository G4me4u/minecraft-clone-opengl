package minecraft.server;

import minecraft.server.net.ServerNetworkManager;

public class MinecraftServer {

	public static void main(String[] args) throws Exception {
		ServerNetworkManager server = new ServerNetworkManager();
		server.bind(8080);
		server.close();
	}
}
