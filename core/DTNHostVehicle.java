package core;

import java.util.List;

import movement.MovementModel;
import routing.MessageRouter;

public class DTNHostVehicle extends DTNHost{

	public DTNHostVehicle(List<MessageListener> msgLs, List<MovementListener> movLs, String groupId,
			List<NetworkInterface> interf, ModuleCommunicationBus comBus, MovementModel mmProto,
			MessageRouter mRouterProto) {
		super(msgLs, movLs, groupId, interf, comBus, mmProto, mRouterProto);
		// TODO Auto-generated constructor stub
	}

	
}
