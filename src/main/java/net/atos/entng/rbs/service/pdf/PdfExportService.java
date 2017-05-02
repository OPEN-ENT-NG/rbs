package net.atos.entng.rbs.service.pdf;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import fr.wseduc.webutils.data.FileResolver;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.shareddata.LocalMap;
import net.atos.entng.rbs.model.ExportRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.StringWriter;
import java.util.Map;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static net.atos.entng.rbs.model.ExportResponse.getView;

public class PdfExportService extends AbstractVerticle implements Handler<Message<JsonObject>> {
	private static final Logger LOG = LoggerFactory.getLogger(PdfExportService.class);

	public static final String PDF_HANDLER_ADDRESS = "rbs.pdf.handler";
	/**
	 * Actions handled by worker
	 */
	public static final String ACTION_CONVERT = "convert";


	private static final String WEEK_HTML_TEMPLATE = "./pdftemplate/booking_week.pdf.xhtml";
	private static final String DAY_HTML_TEMPLATE = "./pdftemplate/booking_day.pdf.xhtml";
	private static final String LIST_HTML_TEMPLATE = "./pdftemplate/booking_list.pdf.xhtml";


	@Override
	public void start() throws Exception {
		super.start();
		vertx.eventBus().consumer(PDF_HANDLER_ADDRESS, this);
	}

	@Override
	public void handle(Message<JsonObject> message) {
		System.out.println("1 INSIDE HANDLE() = "
				+ message.body());

		String action = message.body().getString("action", "");
		JsonObject exportResponse = message.body().getJsonObject("data", new JsonObject());
		String scheme = message.body().getString("scheme", "");
		String host = message.body().getString("host", "");
		switch (action) {
			case ACTION_CONVERT:
				generatePdfFile(exportResponse, scheme, host, message);
				break;
			default:
				JsonObject results = new JsonObject();
				results.put("message", "Unknown action");
				results.put("status", 400);
				message.reply(results);
		}
	}

	private void generatePdfFile(final JsonObject exportResponse, final String scheme, final String host, final Message<JsonObject> message) {
		final String htmlTemplateFile = getTemplate(exportResponse);

		String absolutePath = FileResolver.absolutePath(htmlTemplateFile);

		vertx.fileSystem().readFile(absolutePath, new Handler<AsyncResult<Buffer>>() {
			@Override
			public void handle(AsyncResult<Buffer> result) {
				if (!result.succeeded()) {
					Throwable cause = result.cause();
					LOG.error("Template " + htmlTemplateFile + "  could not be read", cause);
					JsonObject results = new JsonObject();
					results.put("status", 500);
					results.put("message", cause != null ? cause.getMessage() : "");
					message.reply(results);
					return;
				}

				try {
					JsonObject preparedData = prepareData(exportResponse);

					String filledTemplate = fillTemplate(result.result().toString("UTF-8"), preparedData);
					LocalMap<Object, Object> skins = vertx.sharedData().getLocalMap("skins");
					final String baseUrl = scheme + "://" + host + "/assets/themes/" + skins.get(host) + "/img/";
					JsonObject actionObject = new JsonObject();
					actionObject
							.put("content", filledTemplate.getBytes())
							.put("baseUrl", baseUrl);
					String node = (String) vertx.sharedData().getLocalMap("server").get("node");
					if (node == null) {
						node = "";
					}
					vertx.eventBus().send(node + "entcore.pdf.generator", actionObject, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
						public void handle(Message<JsonObject> reply) {
							JsonObject pdfResponse = reply.body();
							if (!"ok".equals(pdfResponse.getString("status"))) {
								String pdfResponseString = pdfResponse.getString("message");
								LOG.error("Conversion error: " + pdfResponseString);
								JsonObject results = new JsonObject();
								results.put("status", 500);
								results.put("message", pdfResponseString);
								message.reply(results);
								return;
							}

							byte[] pdf = pdfResponse.getBinary("content");

							JsonObject results = new JsonObject();
							results.put("status", 200);
							results.put("content", pdf);
							message.reply(results);
						}
					}));

				} catch (Exception e) {
					LOG.error("Conversion Error", e);
					JsonObject results = new JsonObject();
					results.put("status", 500);
					results.put("message", e.getMessage());
					message.reply(results);
					return;
				}
			}


		});
	}

	private JsonObject prepareData(JsonObject jsonExportResponse) {
		JsonFormatter formatter = JsonFormatter.buildFormater(jsonExportResponse);

		JsonObject convertedJson = formatter.format();
		JsonArray jsonFileArray = new fr.wseduc.webutils.collections.JsonArray();
		jsonFileArray.add(convertedJson);

		return new JsonObject().put("export", jsonFileArray);
	}

	private String fillTemplate(String templateAsString, JsonObject preparedData) {
		Mustache.Compiler compiler = Mustache.compiler().defaultValue("");
		Template template = compiler.compile(templateAsString);

		Map<String, Object> ctx = new JsonObject(preparedData.toString()).getMap();

		StringWriter writer = new StringWriter();
		template.execute(ctx, writer);
		return writer.getBuffer().toString();
	}

	private String getTemplate(JsonObject exportResponse) {
		String htmlTemplateFile;
		ExportRequest.View view = getView(exportResponse);
		switch (view) {
			case DAY:
				htmlTemplateFile = DAY_HTML_TEMPLATE;
				break;
			case LIST:
				htmlTemplateFile = LIST_HTML_TEMPLATE;
				break;
			case WEEK:
				htmlTemplateFile = WEEK_HTML_TEMPLATE;
				break;
			default:
				htmlTemplateFile = WEEK_HTML_TEMPLATE;
				break;
		}
		return htmlTemplateFile;
	}


}