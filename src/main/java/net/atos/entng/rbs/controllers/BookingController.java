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

package net.atos.entng.rbs.controllers;

import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import net.atos.entng.rbs.controllers.handler.PeriodicBookingHandlerFactory;
import net.atos.entng.rbs.filters.TypeAndResourceAppendPolicy;
import net.atos.entng.rbs.model.ExportBooking;
import net.atos.entng.rbs.model.ExportRequest;
import net.atos.entng.rbs.model.ExportResponse;
import net.atos.entng.rbs.service.*;
import net.atos.entng.rbs.service.pdf.PdfExportService;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.atos.entng.rbs.BookingStatus.REFUSED;
import static net.atos.entng.rbs.BookingStatus.VALIDATED;
import static net.atos.entng.rbs.BookingUtils.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

public class BookingController extends ControllerHelper {

	private static final I18n i18n = I18n.getInstance();

	private final SchoolService schoolService;
	private final BookingService bookingService;
	private final ResourceService resourceService;
	private BookingNotificationService bookingNotificationService;
	private PeriodicBookingHandlerFactory periodicBookingHandlerFactory;

	public BookingController(EventBus eb) {
		super();
		bookingService = new BookingServiceSqlImpl();
		resourceService = new ResourceServiceSqlImpl();
		schoolService = new SchoolService(eb);
	}

	@Override
	public void init(Vertx vertx, Container container, RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, container, rm, securedActions);
		bookingNotificationService = new BookingNotificationService(BookingController.log, eb, notification, bookingService);
		periodicBookingHandlerFactory = new PeriodicBookingHandlerFactory(BookingController.log, bookingNotificationService, bookingService, resourceService);
	}

	@Post("/resource/:id/booking")
	@ApiDoc("Create booking of a given resource")
	@SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void createBooking(final HttpServerRequest request) {

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "createBooking",
							getCreateBookingHandler(user, request));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	/**
	 * @return Handler to create or update a booking
	 */
	private Handler<JsonObject> getCreateBookingHandler(final UserInfos user,
	                                                    final HttpServerRequest request) {

		return new Handler<JsonObject>() {
			@Override
			public void handle(final JsonObject object) {
				final String resourceId = request.params().get("id");
				final JsonArray slots = object.getArray("slots");
				final long now = getCurrentTimestamp();

				resourceService.getDelaysAndTypeProperties(Long.parseLong(resourceId), new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight() && event.right().getValue() != null) {

							JsonObject resource = event.right().getValue();
							String owner = resource.getString("owner", null);
							String schoolId = resource.getString("school_id", null);
							String jsonString = resource.getString("managers", null);
							JsonArray managers = (jsonString != null) ? new JsonArray(jsonString) : null;

							if (owner == null || schoolId == null) {
								log.warn("Could not get owner or school_id for type of resource " + resourceId);
							}

							if (!canBypassDelaysConstraints(owner, schoolId, user, managers)) {
								for (int i = 0; i < slots.size(); i++) {
									JsonObject slot = slots.get(i);
									long startDate = slot.getLong("start_date", 0L);
									long endDate = slot.getLong("end_date", 0L);
									// check that booking dates respect min and max delays
									if (isDelayLessThanMin(resource, startDate, now)) {
										long nbDays = TimeUnit.DAYS.convert(resource.getLong("min_delay"), TimeUnit.SECONDS);
										String errorMessage = i18n.translate(
												"rbs.booking.bad.request.minDelay.not.respected",
												I18n.acceptLanguage(request),
												Long.toString(nbDays));

										badRequest(request, errorMessage);
										return;
									} else if (isDelayGreaterThanMax(resource, endDate, now)) {
										long nbDays = TimeUnit.DAYS.convert(resource.getLong("max_delay"), TimeUnit.SECONDS);
										String errorMessage = i18n.translate(
												"rbs.booking.bad.request.maxDelay.not.respected",
												I18n.acceptLanguage(request),
												Long.toString(nbDays));

										badRequest(request, errorMessage);
										return;
									}
								}
							}

							Handler<Either<String, JsonArray>> handler = new Handler<Either<String, JsonArray>>() {
								@Override
								public void handle(Either<String, JsonArray> event) {
									if (event.isRight()) {
										JsonArray storageResponse = event.right().getValue();
										JsonArray storageResponses = extractCreationResponses(storageResponse);
										if (storageResponses.size() > 0) {
											for (Object response : storageResponses) {
												bookingNotificationService.notifyBookingCreatedOrUpdated(resourceService, request, user, (JsonObject) response, true, Long.parseLong(resourceId));
											}
											Renders.renderJson(request, storageResponses);
										} else {
											String errorMessage = "rbs.booking.create.conflict";
											JsonObject error = new JsonObject()
													.putString("error", errorMessage);
											renderJson(request, error, 409);
										}
									} else {
										badRequest(request, event.left().getValue());
									}
								}

								private JsonArray extractCreationResponses(JsonArray storageResponse) {
									JsonArray storageResponses = new JsonArray();
									if (storageResponse != null && storageResponse.size() > 0) {
										for (Object o : storageResponse) {
											if (!(o instanceof JsonArray)) continue;
											JsonArray response = (JsonArray) o;
											if (response.size() > 0) {
												for (Object o1 : response) {
													if (!(o1 instanceof JsonObject)) continue;
													JsonObject creationResult = (JsonObject) o1;
													if (creationResult.containsField("start_date")) {
														storageResponses.add(creationResult);
													}
												}
											}
										}
									}
									return storageResponses;
								}
							};

							bookingService.createBooking(resourceId, object, user, handler);
						} else {
							badRequest(request, event.left().getValue());
						}
					}

				});
			}
		};
	}


	/**
	 * @return Handler to update a booking
	 */
	private Handler<JsonObject> getUpdateBookingHandler(final UserInfos user,
	                                                    final HttpServerRequest request) {

		return new Handler<JsonObject>() {
			@Override
			public void handle(final JsonObject object) {
				final String resourceId = request.params().get("id");
				final String bookingId = request.params().get("bookingId");
				final JsonArray slots = object.getArray("slots");
				final long now = getCurrentTimestamp();

				resourceService.getDelaysAndTypeProperties(Long.parseLong(resourceId), new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight() && event.right().getValue() != null) {

							JsonObject resource = event.right().getValue();
							String owner = resource.getString("owner", null);
							String schoolId = resource.getString("school_id", null);
							String jsonString = resource.getString("managers", null);
							JsonArray managers = (jsonString != null) ? new JsonArray(jsonString) : null;

							if (owner == null || schoolId == null) {
								log.warn("Could not get owner or school_id for type of resource " + resourceId);
							}

							if (!canBypassDelaysConstraints(owner, schoolId, user, managers)) {
								JsonObject slot = slots.get(0);
								long startDate = slot.getLong("start_date", 0L);
								long endDate = slot.getLong("end_date", 0L);
								// check that booking dates respect min and max delays
								if (isDelayLessThanMin(resource, startDate, now)) {
									long nbDays = TimeUnit.DAYS.convert(resource.getLong("min_delay"), TimeUnit.SECONDS);
									String errorMessage = i18n.translate(
											"rbs.booking.bad.request.minDelay.not.respected",
											I18n.acceptLanguage(request),
											Long.toString(nbDays));

									badRequest(request, errorMessage);
									return;
								} else if (isDelayGreaterThanMax(resource, endDate, now)) {
									long nbDays = TimeUnit.DAYS.convert(resource.getLong("max_delay"), TimeUnit.SECONDS);
									String errorMessage = i18n.translate(
											"rbs.booking.bad.request.maxDelay.not.respected",
											I18n.acceptLanguage(request),
											Long.toString(nbDays));

									badRequest(request, errorMessage);
									return;
								}
							}
							Handler<Either<String, JsonObject>> handler = new Handler<Either<String, JsonObject>>() {
								@Override
								public void handle(Either<String, JsonObject> event) {
									if (event.isRight()) {
										JsonObject storageResponse = event.right().getValue();
										if (storageResponse != null && storageResponse.size() > 0) {
											bookingNotificationService.notifyBookingCreatedOrUpdated(resourceService, request, user, storageResponse, false, Long.parseLong(resourceId));
											renderJson(request, storageResponse, 200);
										} else {
											JsonObject error = new JsonObject()
													.putString("error", "rbs.booking.update.conflict");
											renderJson(request, error, 409);
										}
									} else {
										badRequest(request, event.left().getValue());
									}
								}
							};
							bookingService.updateBooking(resourceId, bookingId, object, handler);
						} else {
							badRequest(request, event.left().getValue());
						}
					}

				});
			}
		};
	}


	@Post("/resource/:id/booking/periodic")
	@ApiDoc("Create periodic booking of a given resource")
	@SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void createPeriodicBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "createPeriodicBooking",
							periodicBookingHandlerFactory.buildCreationBookingHandler(user, request));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}


	@Put("/resource/:id/booking/:bookingId")
	@ApiDoc("Update booking")
	@SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void updateBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "updateBooking",
							getUpdateBookingHandler(user, request));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Put("/resource/:id/booking/:bookingId/periodic")
	@ApiDoc("Update periodic booking")
	@SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void updatePeriodicBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "updatePeriodicBooking",
							periodicBookingHandlerFactory.buildUpdateBookingHandler(user, request));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Put("/resource/:id/booking/:bookingId/process")
	@ApiDoc("Validate or refuse booking")
	@SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void processBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "processBooking", new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject object) {
							final String resourceId = request.params().get("id");
							final String bookingId = request.params().get("bookingId");

							int newStatus = object.getInteger("status");
							if (newStatus != VALIDATED.status()
									&& newStatus != REFUSED.status()) {
								badRequest(request, "Invalid status");
								return;
							}

							object.putString("moderator_id", user.getUserId());

							Handler<Either<String, JsonArray>> handler = new Handler<Either<String, JsonArray>>() {
								@Override
								public void handle(Either<String, JsonArray> event) {
									if (event.isRight()) {
										if (event.right().getValue() != null && event.right().getValue().size() >= 3) {
											final JsonArray results = event.right().getValue();
											try {
												final JsonObject processedBooking = ((JsonArray) results.get(2)).get(0);

												Handler<Either<String, JsonObject>> notifHandler = new Handler<Either<String, JsonObject>>() {
													@Override
													public void handle(Either<String, JsonObject> event) {
														if (event.isRight() && event.right().getValue() != null
																&& event.right().getValue().size() > 0) {

															final String resourceName = event.right().getValue().getString("resource_name");
															bookingNotificationService.notifyBookingProcessed(resourceService, request, user, processedBooking, resourceName, Long.parseLong(resourceId));
															if (results.size() >= 4) {
																JsonArray concurrentBookings = results.get(3);
																for (Object o : concurrentBookings) {
																	JsonObject booking = (JsonObject) o;
																	bookingNotificationService.notifyBookingProcessed(resourceService, request, user, booking, resourceName, Long.parseLong(resourceId));
																}
															}

														} else {
															log.error("Error when calling service getResourceName. Unable to send timeline notification.");
														}
													}
												};

												bookingService.getResourceName(bookingId, notifHandler);
												renderJson(request, processedBooking);
											} catch (Exception e) {
												log.error("Unable to send timeline notification. Error when processing response from worker mod-mysql-postgresql :");
												log.error(e.getMessage(), e);
												renderJson(request, event.right().getValue());
											}
										} else {
											log.error("Unable to send timeline notification. Response from worker mod-mysql-postgresql is null or has a size < 3");
											renderJson(request, new JsonObject());
										}
									} else {
										badRequest(request, event.left().getValue());
									}
								}
							};

							bookingService.processBooking(resourceId,
									bookingId, newStatus, object, user, handler);
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Delete("/resource/:id/booking/:bookingId/:booleanThisAndAfter")
	@ApiDoc("Delete booking")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void deleteBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					final String bookingId = request.params().get("bookingId");
					final String resourceId = request.params().get("id");
					bookingService.getBookingWithResourceName(bookingId, new Handler<Either<String, JsonObject>>() {
						@Override
						public void handle(Either<String, JsonObject> event) {
							if (event.isRight()) {
								if (event.right().getValue() != null && event.right().getValue().size() > 0) {
									final JsonObject booking = event.right().getValue();
									String thisAndAfterString = request.params().get("booleanThisAndAfter");
									boolean testString = false;
									if (thisAndAfterString.equals("true")) {
										testString = true;
									}
									final boolean thisAndAfter = testString;
									if (thisAndAfter) {
										if (booking.getLong("parent_booking_id") != null) {
											bookingService.getParentBooking(bookingId, new Handler<Either<String, JsonObject>>() {
												@Override
												public void handle(Either<String, JsonObject> event) {
													try {
														JsonObject parentBooking = event.right().getValue();
														final long pId = parentBooking.getLong("id", 0L);
														final String pStartDate = parentBooking.getString("start_date", null);
														final String pEndDate = parentBooking.getString("end_date", null);
														final Date endDate = parseDateFromDB(pEndDate);
														long now = Calendar.getInstance().getTimeInMillis();
														if (endDate.getTime() < now) {
															String errorMessage = i18n.translate(
																	"rbs.booking.bad.request.deletion.periodical.booking.already.terminated",
																	Renders.getHost(request),
																	I18n.acceptLanguage(request));
															badRequest(request, errorMessage);
															return;
														}
														final Date startDate = parseDateFromDB(booking.getString("start_date"));
														try {
															bookingService.deleteFuturePeriodicBooking(String.valueOf(pId), startDate,
																	new Handler<Either<String, JsonArray>>() {
																		@Override
																		public void handle(Either<String, JsonArray> event) {
																			if (event.isRight()) {
																				bookingNotificationService.notifyPeriodicBookingCreatedOrUpdated(resourceService, request, user, event.right().getValue(), false, Long.parseLong(resourceId));
																				Renders.renderJson(request, event.right().getValue());
																			} else {
																				badRequest(request, event.left().getValue());
																			}
																		}
																	});
														} catch (Exception e) {
															log.error("Error during service deleteFuturePeriodicBooking", e);
															renderError(request);
														}
														return;
													} catch (ParseException e) {
														log.error("Can't parse date form RBS DB", e);
													}
												}
											});
										} else {
											String errorMessage = i18n.translate(
													"rbs.booking.bad.request.deletion.booking.with.true",
													Renders.getHost(request),
													I18n.acceptLanguage(request));
											badRequest(request, errorMessage);
											return;
										}
									} else {
										try {
											long now = Calendar.getInstance().getTimeInMillis();
											final String endDate = booking.getString("end_date");
											if (booking.getLong("parent_booking_id") != null && parseDateFromDB(endDate).getTime() < now) {
												String errorMessage = i18n.translate(
														"rbs.booking.bad.request.deletion.periodical.booking.already.terminated",
														Renders.getHost(request),
														I18n.acceptLanguage(request));
												badRequest(request, errorMessage);
												return;
											}
										} catch (ParseException e) {
											e.printStackTrace();
										}
										bookingService.delete(bookingId, user, new Handler<Either<String, JsonObject>>() {
											@Override
											public void handle(Either<String, JsonObject> event) {
												if (event.isRight()) {
													if (event.right().getValue() != null && event.right().getValue().size() > 0) {
														bookingNotificationService.notifyBookingDeleted(resourceService, request, user, booking, bookingId, Long.parseLong(resourceId));
														Renders.renderJson(request, event.right().getValue(), 204);
													} else {
														notFound(request);
													}
												} else {
													badRequest(request, event.left().getValue());
												}
											}
										});
									}

								} else {
									notFound(request);
								}
							} else {
								badRequest(request, event.left().getValue());
							}
						}
					});

				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}


	/*
	 * TODO review the ux functional with clients to allow paging in listing view.
	 */
	@Get("/bookings/all")
	@ApiDoc("List all bookings")
	@SecuredAction("rbs.booking.list.all")
	public void listAllBookings(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					final List<String> groupsAndUserIds = getUserIdAndGroupIds(user);
					bookingService.listAllBookings(user, groupsAndUserIds, arrayResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Get("/booking/:id")
	@ApiDoc("find start date of one booking")
	@SecuredAction(value = "rbs.booking.one.id", type = ActionType.AUTHENTICATED)
	public void getBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					bookingService.getBooking(request.params().get("id"), new Handler<Either<String, JsonObject>>() {
						@Override
						public void handle(Either<String, JsonObject> event) {
							if (event.isRight()) {
								Renders.renderJson(request, event.right().getValue());
							} else {
								Renders.renderError(request, new JsonObject().putString("error", event.left().getValue()));
							}
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Get("/bookings/all/:startdate/:enddate")
	@ApiDoc("List all bookings by dates")
	@SecuredAction("rbs.booking.list.all.dates")
	public void listAllBookingsByDate(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					final String startDate = request.params().get("startdate");
					final String endDate = request.params().get("enddate");
					if (startDate != null && endDate != null &&
							startDate.matches("\\d{4}-\\d{2}-\\d{2}") && endDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
						final List<String> groupsAndUserIds = getUserIdAndGroupIds(user);

						bookingService.listAllBookingsByDates(user, groupsAndUserIds, startDate, endDate, new Handler<Either<String, JsonArray>>() {
							@Override
							public void handle(Either<String, JsonArray> event) {
								if (event.isRight()) {
									Renders.renderJson(request, event.right().getValue());
								} else {
									JsonObject error = new JsonObject()
											.putString("error", event.left().getValue());
									Renders.renderJson(request, error, 400);
								}
							}
						});
					} else {
						Renders.badRequest(request, "params start and end date must be defined with YYYY-MM-DD format !");
					}
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Get("/bookings/full/slots/:bookingId")
	@ApiDoc("List all booking slots of a periodic booking")
	@SecuredAction(value = "rbs.booking.list.slots")
	public void listFullSlotsBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					final String bookingId = request.params().get("bookingId");
					if (bookingId != null) {

						bookingService.listFullSlotsBooking(bookingId, new Handler<Either<String, JsonArray>>() {
							@Override
							public void handle(Either<String, JsonArray> event) {
								if (event.isRight()) {
									Renders.renderJson(request, event.right().getValue());
								} else {
									JsonObject error = new JsonObject()
											.putString("error", event.left().getValue());
									Renders.renderJson(request, error, 400);
								}
							}
						});
					} else {
						Renders.badRequest(request, "param booking id must be defined !");
					}
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Get("/bookings")
	@ApiDoc("List all bookings created by current user")
	@SecuredAction("rbs.booking.list")
	public void listUserBookings(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					bookingService.listUserBookings(user, arrayResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Get("/resource/:id/bookings")
	@ApiDoc("List all bookings for a given resource")
	@SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void listBookingsByResource(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					String resourceId = request.params().get("id");
					bookingService.listBookingsByResource(resourceId, arrayResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Get("/bookings/unprocessed")
	@ApiDoc("List all bookings waiting to be processed by current user")
	@SecuredAction("rbs.booking.list.unprocessed")
	public void listUnprocessedBookings(final HttpServerRequest request) {

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					final List<String> groupsAndUserIds = getUserIdAndGroupIds(user);

					bookingService.listUnprocessedBookings(groupsAndUserIds, user, arrayResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});

	}

	@Post("/bookings/export")
	@ApiDoc("Export bookings in requested format")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void exportICal(final HttpServerRequest request) {
		RequestUtils.bodyToJson(request, pathPrefix + "exportBookings", new Handler<JsonObject>() {
			@Override
			public void handle(final JsonObject userExportRequest) {

				UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
					@Override
					public void handle(final UserInfos user) {
						if (user != null) {
							ExportRequest exportRequest;
							try {
								exportRequest = new ExportRequest(userExportRequest, user);
							} catch (IllegalArgumentException e) {
								Renders.badRequest(request, e.getMessage());
								return;
							}
							final ExportResponse exportResponse = new ExportResponse(exportRequest);
							bookingService.getBookingsForExport(exportRequest, new Handler<Either<String, List<ExportBooking>>>() {
								@Override
								public void handle(final Either<String, List<ExportBooking>> event) {
									if (event.isRight()) {
										final List<ExportBooking> exportBookings = event.right().getValue();
										if (exportBookings.isEmpty()) {
											JsonObject error = new JsonObject()
													.putString("error", "No booking in search period");
											Renders.renderJson(request, error, 204);
											return;
										}
										exportResponse.setBookings(exportBookings);

										schoolService.getSchoolNames(new Handler<Either<String, Map<String, String>>>() {
											@Override
											public void handle(Either<String, Map<String, String>> event) {
												if (event.isRight()) {
													exportResponse.setSchoolNames(event.right().getValue());

													if (exportResponse.getRequest().getFormat().equals(ExportRequest.Format.PDF)) {
														generatePDF(request, exportResponse);
													} else {
														generateICal(request, exportResponse);
													}
												} else {
													JsonObject error = new JsonObject()
															.putString("error", event.left().getValue());
													Renders.renderJson(request, error, 400);
												}
											}
										});
									} else {
										JsonObject error = new JsonObject()
												.putString("error", event.left().getValue());
										Renders.renderJson(request, error, 400);
									}
								}
							});

						} else {
							log.debug("User not found in session.");
							unauthorized(request);
						}
					}
				});
			}
		});
	}

	private void generateICal(final HttpServerRequest request, final ExportResponse exportResponse) {
		final JsonObject conversionRequest = new JsonObject();
		conversionRequest.putString("action", PdfExportService.ACTION_CONVERT);
		final JsonObject dataToExport = exportResponse.toJson();
		conversionRequest.putObject("data", dataToExport);
		eb.send(IcalExportService.ICAL_HANDLER_ADDRESS, conversionRequest, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> message) {
				JsonObject body = message.body();
				Number status = body.getNumber("status");
				if (status.intValue() == 200) {
					String icsContent = body.getString("content");
					request.response().putHeader("Content-Type", "text/calendar");
					request.response().putHeader("Content-Disposition",
							"attachment; filename=export.ics");
					request.response().end(new Buffer(icsContent));
				} else {
					String errorMessage = body.getString("message");
					log.warn("An error occurred during ICal generation of " + exportResponse + " : " + errorMessage);
					JsonObject error = new JsonObject()
							.putString("error", "ICal generation: " + errorMessage)
							.putObject("dataToExport", dataToExport);
					Renders.renderError(request, error);
				}
			}
		});
	}

	private void generatePDF(final HttpServerRequest request, final ExportResponse exportResponse) {
		final JsonObject conversionRequest = new JsonObject();
		conversionRequest.putString("action", PdfExportService.ACTION_CONVERT);
		final JsonObject dataToExport = exportResponse.toJson();
		conversionRequest.putObject("data", dataToExport);
		conversionRequest.putString("scheme", getScheme(request));
		conversionRequest.putString("host", Renders.getHost(request));

		eb.send(PdfExportService.PDF_HANDLER_ADDRESS, conversionRequest, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> message) {
				JsonObject body = message.body();
				Number status = body.getNumber("status");
				if (status.intValue() == 200) {
					byte[] pdfContent = body.getBinary("content");
					request.response().putHeader("Content-Type", "application/pdf");
					request.response().putHeader("Content-Disposition",
							"attachment; filename=export.pdf");
					request.response().end(new Buffer(pdfContent));
				} else {
					String errorMessage = body.getString("message");
					log.warn("An error occurred during PDF generation of " + exportResponse + " : " + errorMessage);
					JsonObject error = new JsonObject()
							.putString("error", "PDF generation: " + errorMessage)
							.putObject("dataToExport", dataToExport);
					Renders.renderError(request, error);
				}
			}
		});

	}


}
