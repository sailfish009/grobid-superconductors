package org.grobid.service.controller;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.grobid.core.data.Superconductor;
import org.grobid.core.document.Document;
import org.grobid.core.engines.SuperconductorsParser;
import org.grobid.core.layout.Page;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.IOUtilities;
import org.grobid.core.utilities.Pair;
import org.grobid.service.configuration.GrobidSuperconductorsConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;

@Singleton
@Path("/")
public class AnnotationController {

    private static final String PATH_BASE = "/";
    private static final String PATH_IS_ALIVE = "isalive";

    private SuperconductorsParser superconductorsParser;


    @Inject
    public AnnotationController(GrobidSuperconductorsConfiguration configuration, SuperconductorsParser superconductorsParser) {
        this.superconductorsParser = superconductorsParser;
    }

    @Path(PATH_IS_ALIVE)
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public static Response isAlive() {
        Response response = null;
        try {

            String retVal = null;
            try {
                retVal = Boolean.valueOf(true).toString();
            } catch (Exception e) {
                retVal = Boolean.valueOf(false).toString();
            }
            response = Response.status(Response.Status.OK).entity(retVal).build();
        } catch (Exception e) {
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    @Path("annotateSuperconductorsPDF")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json")
    @POST
    public Response processPDF(final InputStream inputStream) {
        Response response = null;
        File originFile = null;

        try {
            LibraryLoader.load();
            originFile = IOUtilities.writeInputFile(inputStream);

            if (originFile == null) {
                response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            } else {
                long start = System.currentTimeMillis();
                Pair<List<Superconductor>, Document> extractedEntities = superconductorsParser.extractFromPDF(originFile);
                long end = System.currentTimeMillis();

                Document doc = extractedEntities.getB();
                List<Superconductor> measurements = extractedEntities.getA();
                StringBuilder json = new StringBuilder();
                json.append("{ ");

                // page height and width
                json.append("\"pages\":[");
                List<Page> pages = doc.getPages();
                boolean first = true;
                for (Page page : pages) {
                    if (first)
                        first = false;
                    else
                        json.append(", ");
                    json.append("{\"page_height\":" + page.getHeight());
                    json.append(", \"page_width\":" + page.getWidth() + "}");
                }

                json.append("], \"superconductors\":[");
                first = true;
                for (Superconductor entity : measurements) {
                    if (!first)
                        json.append(", ");
                    else
                        first = false;
                    json.append(entity.toJson());
                }

                json.append("]");
                json.append(", \"runtime\" :" + (end - start));
                json.append("}");

                if (json != null) {
                    response = Response
                            .ok()
                            .type("application/json")
                            .entity(json.toString())
                            .build();
                } else {
                    response = Response.status(Response.Status.NO_CONTENT).build();
                }
            }
        } catch (NoSuchElementException nseExp) {
            response = Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception exp) {
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            IOUtilities.removeTempFile(originFile);
        }
        return response;
    }

    @Path("processSuperconductorsText")
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Response processTextSuperconductors(@FormDataParam("text") String text) {
        Response response = null;

        try {
            long start = System.currentTimeMillis();
            List<Superconductor> measurements = superconductorsParser.process(text);
            long end = System.currentTimeMillis();

            StringBuilder jsonBuilder = null;
            if (measurements != null) {
                jsonBuilder = new StringBuilder();
                jsonBuilder.append("{ ");
                jsonBuilder.append("\"runtime\" : " + (end - start));
                jsonBuilder.append(", \"superconductors\" : [ ");
                boolean first = true;
                for (Superconductor measurement : measurements) {
                    if (first)
                        first = false;
                    else
                        jsonBuilder.append(", ");
                    jsonBuilder.append(measurement.toJson());
                }
                jsonBuilder.append("] }");
            } else
                response = Response.status(Response.Status.NO_CONTENT).build();

            if (jsonBuilder != null) {
                //System.out.println(jsonBuilder.toString());
                response = Response.status(Response.Status.OK).entity(jsonBuilder.toString())
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
                        .build();
            }
        } catch (NoSuchElementException nseExp) {
            response = Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            String message = "Error in " + e.getStackTrace()[0].toString();
            if (e.getCause() != null) {
                message = e.getCause().getMessage();
            }
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(message).build();
        }
        return response;
    }
}
