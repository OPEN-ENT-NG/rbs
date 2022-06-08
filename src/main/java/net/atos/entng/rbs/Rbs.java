/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.rbs;

import io.vertx.core.DeploymentOptions;
import net.atos.entng.rbs.controllers.*;
import net.atos.entng.rbs.events.RbsRepositoryEvents;
import net.atos.entng.rbs.events.RbsSearchingEvents;
import net.atos.entng.rbs.filters.TypeAndResourceAppendPolicy;
import net.atos.entng.rbs.filters.TypeOwnerSharedOrLocalAdmin;
import net.atos.entng.rbs.service.BookingServiceSqlImpl;
import net.atos.entng.rbs.service.IcalExportService;
import net.atos.entng.rbs.service.pdf.PdfExportService;
import org.entcore.common.http.BaseServer;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.share.impl.SqlShareService;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;

public class Rbs extends BaseServer {

	public final static String RBS_NAME = "RBS";
	public final static String BOOKING_TABLE_NAME = "booking";
	public final static String RESOURCE_TABLE_NAME = "resource";
	public final static String RESOURCE_SHARE_TABLE_NAME = "resource_shares";
	public final static String RESOURCE_TYPE_TABLE_NAME = "resource_type";
	public final static String RESOURCE_TYPE_SHARE_TABLE_NAME = "resource_type_shares";

	public static String DB_SCHEMA;
	public static String AVAILABILITY_TABLE;
	public static String BOOKING_TABLE;
	public static String GROUPS_TABLE;
	public static String MEMBERS_TABLE;
	public static String NOTIFICATIONS_TABLE;
	public static String RESOURCE_TABLE;
	public static String RESOURCE_SHARES_TABLE;
	public static String RESOURCE_TYPE_TABLE;
	public static String RESOURCE_TYPE_SHARES_TABLE;
	public static String USERS_TABLE;

	@Override
	public void start() throws Exception {
		super.start();
		final EventBus eb = getEventBus(vertx);

		// Set RepositoryEvents implementation used to process events published for transition
		setRepositoryEvents(new RbsRepositoryEvents());
		if (config.getBoolean("searching-event", true)) {
			setSearchingEvents(new RbsSearchingEvents());
		}

		// Store tables' SQL name in constants
		DB_SCHEMA = "rbs";
		AVAILABILITY_TABLE = DB_SCHEMA + ".availability";
		BOOKING_TABLE = DB_SCHEMA + ".booking";
		GROUPS_TABLE = DB_SCHEMA + ".groups";
		MEMBERS_TABLE = DB_SCHEMA + ".members";
		NOTIFICATIONS_TABLE = DB_SCHEMA + ".notifications";
		RESOURCE_TABLE = DB_SCHEMA + ".resource";
		RESOURCE_SHARES_TABLE = DB_SCHEMA + ".resource_shares";
		RESOURCE_TYPE_TABLE = DB_SCHEMA + ".resource_type";
		RESOURCE_TYPE_SHARES_TABLE = DB_SCHEMA + ".resource_type_shares";
		USERS_TABLE = DB_SCHEMA + ".users";


		// Controllers
		addController(new DisplayController());

		SqlConf confType = SqlConfs.createConf(ResourceTypeController.class.getName());
		confType.setTable(RESOURCE_TYPE_TABLE_NAME);
		confType.setShareTable(RESOURCE_TYPE_SHARE_TABLE_NAME);
		confType.setSchema(getSchema());
		ResourceTypeController typeController = new ResourceTypeController(eb);
		SqlCrudService typeSqlCrudService = new SqlCrudService(getSchema(), RESOURCE_TYPE_TABLE_NAME, RESOURCE_TYPE_SHARE_TABLE_NAME,
				new fr.wseduc.webutils.collections.JsonArray().add("*"), new JsonArray().add("*"), true);
		typeController.setCrudService(typeSqlCrudService);
		typeController.setShareService(new SqlShareService(getSchema(), RESOURCE_TYPE_SHARE_TABLE_NAME,
				eb, securedActions, null));
		addController(typeController);

		SqlConf confResource = SqlConfs.createConf(ResourceController.class.getName());
		confResource.setTable(RESOURCE_TABLE_NAME);
		confResource.setShareTable(RESOURCE_SHARE_TABLE_NAME);
		confResource.setSchema(getSchema());
		ResourceController resourceController = new ResourceController();
		SqlCrudService resourceSqlCrudService = new SqlCrudService(getSchema(), RESOURCE_TABLE_NAME, RESOURCE_SHARE_TABLE_NAME,
				new fr.wseduc.webutils.collections.JsonArray().add("*"), new JsonArray().add("*"), true);
		resourceController.setCrudService(resourceSqlCrudService);
		resourceController.setShareService(new SqlShareService(getSchema(), RESOURCE_SHARE_TABLE_NAME,
				eb, securedActions, null));
		addController(resourceController);


		DeploymentOptions options = new DeploymentOptions().setWorker(true);
		vertx.deployVerticle(new PdfExportService(), options);
		vertx.deployVerticle(new IcalExportService(), options);

		addController(new BookingController(eb));
		addController(new AvailabilityController());
		addController(new EventBusController(new BookingServiceSqlImpl(), new TypeAndResourceAppendPolicy()));

		setDefaultResourceFilter(new TypeOwnerSharedOrLocalAdmin());
	}

}