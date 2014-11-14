package net.atos.entng.rbs;

import net.atos.entng.rbs.controllers.BookingController;
import net.atos.entng.rbs.controllers.DisplayController;
import net.atos.entng.rbs.controllers.ResourceController;
import net.atos.entng.rbs.controllers.ResourceTypeController;

import org.entcore.common.http.BaseServer;
import org.entcore.common.http.filter.sql.ShareAndOwner;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.share.impl.SqlShareService;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.vertx.java.core.json.JsonArray;

public class Rbs extends BaseServer {

	public final static String RBS_NAME = "RBS";
	public final static String BOOKING_TABLE = "booking";
	public final static String RESOURCE_TABLE = "resource";
	public final static String RESOURCE_SHARE_TABLE = "resource_shares";
	public final static String RESOURCE_TYPE_TABLE = "resource_type";
	public final static String RESOURCE_TYPE_SHARE_TABLE = "resource_type_shares";

	@Override
	public void start() {
		super.start();

		addController(new DisplayController());

		SqlConf confType = SqlConfs.createConf(ResourceTypeController.class.getName());
		confType.setTable(RESOURCE_TYPE_TABLE);
		confType.setShareTable(RESOURCE_TYPE_SHARE_TABLE);
		confType.setSchema(getSchema());
		ResourceTypeController typeController = new ResourceTypeController();
		SqlCrudService typeSqlCrudService = new SqlCrudService(getSchema(), RESOURCE_TYPE_TABLE, RESOURCE_TYPE_SHARE_TABLE,
				new JsonArray().addString("*"), new JsonArray().add("*"), true);
		typeController.setCrudService(typeSqlCrudService);
		typeController.setShareService(new SqlShareService(getSchema(),RESOURCE_TYPE_SHARE_TABLE,
				getEventBus(vertx), securedActions, null));
		addController(typeController);

		SqlConf confResource = SqlConfs.createConf(ResourceController.class.getName());
		confResource.setTable(RESOURCE_TABLE);
		confResource.setShareTable(RESOURCE_SHARE_TABLE);
		confResource.setSchema(getSchema());
		ResourceController resourceController = new ResourceController();
		SqlCrudService resourceSqlCrudService = new SqlCrudService(getSchema(), RESOURCE_TABLE, RESOURCE_SHARE_TABLE,
				new JsonArray().addString("*"), new JsonArray().add("*"), true);
		resourceController.setCrudService(resourceSqlCrudService);
		resourceController.setShareService(new SqlShareService(getSchema(),RESOURCE_SHARE_TABLE,
				getEventBus(vertx), securedActions, null));
		addController(resourceController);

		BookingController bookingController = new BookingController();
		addController(bookingController);

		setDefaultResourceFilter(new ShareAndOwner());
	}

}