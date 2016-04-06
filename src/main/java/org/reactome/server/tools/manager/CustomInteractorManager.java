package org.reactome.server.tools.manager;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeTypes;
import org.reactome.server.tools.exception.*;
import org.reactome.server.tools.interactors.exception.CustomPsicquicInteractionClusterException;
import org.reactome.server.tools.interactors.model.Interaction;
import org.reactome.server.tools.interactors.model.InteractionDetails;
import org.reactome.server.tools.interactors.model.Interactor;
import org.reactome.server.tools.interactors.service.PsicquicService;
import org.reactome.server.tools.interactors.tuple.exception.ParserException;
import org.reactome.server.tools.interactors.tuple.exception.TupleParserException;
import org.reactome.server.tools.interactors.tuple.model.*;
import org.reactome.server.tools.interactors.tuple.util.ParserUtils;
import org.reactome.server.tools.interactors.util.InteractorConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

@Controller
public class CustomInteractorManager {

    private static Logger logger = Logger.getLogger(CustomInteractorManager.class);

    @Autowired
    PsicquicService psicquicService;

    @Autowired
    CommonsMultipartResolver multipartResolver;

    private TupleResult getUserDataContainer(String name, String filename, InputStream is) {
        TupleResult result = null;
        try {
            result = ParserUtils.getUserDataContainer(name, filename, is);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TupleParserException e) {
            throw new DataFormatException(e.getErrorMessages());
        } catch (ParserException e) {
            throw new DataFormatException(e.getMessage());
        }

        return result;
    }

    public TupleResult getUserDataContainerFromContent(String name, String input) {
        return getUserDataContainer(name, null, IOUtils.toInputStream(input));
    }

    public TupleResult getUserDataContainerFromFile(String name, MultipartFile file) {
        if (!file.isEmpty()) {
            try {
                String mimeType = detectMimeType(TikaInputStream.get(file.getInputStream()));

                if (!isAcceptedContentType(mimeType)) {
                    throw new UnsupportedMediaTypeException();
                }

                return getUserDataContainer(name, file.getOriginalFilename(), file.getInputStream());

            } catch (IOException | NoSuchMethodError e) {
                throw new UnsupportedMediaTypeException();
            }
        }

        throw new UnsupportedMediaTypeException();

    }

    public TupleResult getUserDataContainerFromURL(String name, String filename, String url) {
        if (url != null && !url.isEmpty()) {
            InputStream is;
            try {
                /** Check in the URL if the filename is encoded **/
                String decodeFilename = URLDecoder.decode(filename, "UTF-8");
                boolean encoded = !filename.equals(decodeFilename);

                if (!encoded) {
                    String encodeFilename = URLEncoder.encode(filename, "UTF-8");
                    encodeFilename = encodeFilename.replaceAll("\\+", "%20");
                    url = url.replace(filename, encodeFilename);
                }

                HttpURLConnection conn;
                URL aux = new URL(url);

                if (aux.getProtocol().contains("https")) {
                    doTrustToCertificates(); //accepting the certificate by default
                    HttpsURLConnection tmpConn = (HttpsURLConnection) aux.openConnection();
                    is = tmpConn.getInputStream();
                    conn = tmpConn;
                } else {
                    URLConnection tmpConn = aux.openConnection();
                    is = tmpConn.getInputStream();
                    conn = (HttpURLConnection) tmpConn;
                }

                if (conn.getContentLength() > multipartResolver.getFileUpload().getSizeMax()) {
                    throw new RequestEntityTooLargeException();
                }

                String mimeType = detectMimeType(TikaInputStream.get(aux));
                if (!isAcceptedContentType(mimeType)) {
                    throw new UnsupportedMediaTypeException();
                }

            } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
                e.printStackTrace();
                throw new UnprocessableEntityException();
            }

            return getUserDataContainer(name, filename, is);

        }

        throw new UnsupportedMediaTypeException();
    }

    public TupleResult registryCustomPsicquic(String name, String psicquicURL) {
        //InputStream is;
        try {
            HttpURLConnection conn;
            URL aux = new URL(psicquicURL);
            if (aux.getProtocol().contains("https")) {
                doTrustToCertificates(); //accepting the certificate by default
                conn = (HttpsURLConnection) aux.openConnection();
                //conn = tmpConn;
            } else {
                URLConnection tmpConn = aux.openConnection();
                conn = (HttpURLConnection) tmpConn;
            }

            // THIS VALIDATION IS NOT WORKING. PSIQUIC Service does not have landing page, then it returns 404
            /*if (conn.getResponseCode() != HttpStatus.OK.value()) {
                throw new UnreachableException(conn.getResponseCode());
            }*/

        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new UnprocessableEntityException();
        }

        try {
            return ParserUtils.processCustomPsicquic(name, psicquicURL);
        } catch (TupleParserException e) {
            throw new DataFormatException(e.getErrorMessages());
        } catch (ParserException e) {
            throw new DataFormatException(e.getMessage());
        }
    }

    /**
     * Accepts certificate in case user provides a https url
     * REMARKED: Copied from Analysis project.
     */
    private void doTrustToCertificates() throws NoSuchAlgorithmException, KeyManagementException {
        Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    }
                }
        };

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String urlHostName, SSLSession session) {
                if (!urlHostName.equalsIgnoreCase(session.getPeerHost())) {
                    logger.warn("Warning: URL host '" + urlHostName + "' is different to SSLSession host '" + session.getPeerHost() + "'.");
                }
                return true;
            }
        };
        HttpsURLConnection.setDefaultHostnameVerifier(hv);
    }

    /**
     * Gets CustomInteraction set and convert it into Interactions
     */
    private List<Interaction> convertCustomInteraction(String searchTerm, Set<CustomInteraction> customInteractionSet) {
        List<Interaction> interactions = new ArrayList<>(customInteractionSet.size());

        for (CustomInteraction customInteraction : customInteractionSet) {
            Interaction interaction = new Interaction();

            /** create interactor A **/
            Interactor interactorA = new Interactor();
            interactorA.setAcc(customInteraction.getInteractorIdA());
            interactorA.setAlias(customInteraction.getAliasInteractorA());
            if (StringUtils.isNotEmpty(customInteraction.getTaxonomyIdInteractorA())) {
                interactorA.setTaxid(Integer.parseInt(customInteraction.getTaxonomyIdInteractorA()));
            }
            interactorA.setSynonyms(customInteraction.getAlternativeInteractorA());

            /** create interactor A **/
            Interactor interactorB = new Interactor();
            interactorB.setAcc(customInteraction.getInteractorIdB());
            interactorB.setAlias(customInteraction.getAliasInteractorB());
            if (StringUtils.isNotEmpty(customInteraction.getTaxonomyIdInteractorB())) {
                interactorB.setTaxid(Integer.parseInt(customInteraction.getTaxonomyIdInteractorB()));
            }
            interactorB.setSynonyms(customInteraction.getAlternativeInteractorB());

            /** keep the search term, always in side A **/
            if (searchTerm.equals(interactorA.getAcc())) {
                interaction.setInteractorA(interactorA);
                interaction.setInteractorB(interactorB);
            } else {
                interaction.setInteractorA(interactorB);
                interaction.setInteractorB(interactorA);
            }

            /** set score **/
            if (customInteraction.getConfidenceValue() != null) {
                interaction.setIntactScore(customInteraction.getConfidenceValue());
            }

            /** set evidences list **/
            if (StringUtils.isNotEmpty(customInteraction.getInteractionEvidence())) {
                InteractionDetails evidences = new InteractionDetails();
                evidences.setInteractionAc(customInteraction.getInteractionEvidence());

                interaction.addInteractionDetails(evidences);
            }

            /** add into interactions list **/
            interactions.add(interaction);

        }

        Collections.sort(interactions);
        Collections.reverse(interactions);

        return interactions;

    }

    /**
     * Of a given token and (list) proteins retrieve all interactions.
     * The token is associated to a data file previously submitted.
     *
     * @return for a given pr
     */
    public Map<String, List<Interaction>> getInteractionsByTokenAndProteins(String tokenStr, Set<String> proteins) {
        Map<String, List<Interaction>> interactionMap;

        /**
         * Check if token exists in the Repository.
         */
        if (!CustomInteractorRepository.getKeys().contains(tokenStr) && !CustomPsicquicRepository.getKeys().contains(tokenStr)) {
            throw new TokenNotFoundException(tokenStr);
        }

        if (tokenStr.startsWith(InteractorConstant.TUPLE_PREFIX)) {
            interactionMap = getInteractorFromCustomPsicquic(tokenStr, proteins);
        } else {
            interactionMap = getInteractorFromCustomDataSubmission(tokenStr, proteins);
        }

        return interactionMap;
    }

    private Map<String, List<Interaction>> getInteractorFromCustomDataSubmission(String tokenStr, Set<String> proteins) {
        Map<String, List<Interaction>> interactionMap = new HashMap<>();

        /** Retrieve stored summary associated with given token **/
        UserDataContainer udc = CustomInteractorRepository.getByToken(tokenStr);

        /** Get custom interactions **/
        Collection<CustomInteraction> allInteractions = udc.getCustomInteractions();

        for (String singleAccession : proteins) {
            Set<CustomInteraction> customInteractionSet = new HashSet<>();

            // Check if singleAccession contains in A or B in the Interaction List
            for (CustomInteraction cust : allInteractions) {
                if (singleAccession.equals(cust.getInteractorIdA()) ||
                        singleAccession.equals(cust.getInteractorIdB())) {

                    //Ok, interacts with something.
                    customInteractionSet.add(cust);
                }
            }

            List<Interaction> interactions = convertCustomInteraction(singleAccession, customInteractionSet);

            interactionMap.put(singleAccession, interactions);

        }

        return interactionMap;
    }

    private Map<String, List<Interaction>> getInteractorFromCustomPsicquic(String tokenStr, Set<String> proteins) {
        Map<String, List<Interaction>> interactionMap;

        /** Retrieve stored summary associated with given token **/
        String url = CustomPsicquicRepository.getByToken(tokenStr);

        try {
            interactionMap = psicquicService.getInteractionFromCustomPsicquic(url, proteins);
        } catch (CustomPsicquicInteractionClusterException e) {
            throw new PsicquicContentException("Error querying your PSICQUIC Resource.");
        }

        return interactionMap;
    }

    /**
     * Retrieve the filename of a given url
     *
     * @return String as filename
     */
    public String getFileNameFromURL(String url) {
        String name = "";
        if (url != null && !url.isEmpty()) {
            try {
                name = FilenameUtils.getName((new URL(url)).getFile());
            } catch (MalformedURLException e) {
                /*Nothing here*/
            }
        }
        return name;
    }

    /**
     * Detect MimeType using apache tika.
     * jMimeMagic has failed when analysing the PSIMITAB .txt file export from IntAct page
     *
     * @throws IOException
     */
    public String detectMimeType(TikaInputStream tikaInputStream) throws IOException {
        final Detector DETECTOR = new DefaultDetector(MimeTypes.getDefaultMimeTypes());

        try {
            return DETECTOR.detect(tikaInputStream, new Metadata()).toString();
        } finally {
            if (tikaInputStream != null) {
                tikaInputStream.close();
            }
        }
    }

    private boolean isAcceptedContentType(String contentType) {
        return contentType.contains("text/plain") || contentType.contains("text/csv");
    }

}