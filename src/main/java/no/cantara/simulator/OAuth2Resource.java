package no.cantara.simulator;

import no.cantara.service.commands.oauth2.CommandVerifyToken;
import no.cantara.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.MalformedURLException;

public class OAuth2Resource {

    private static final Logger log = LoggerFactory.getLogger(OAuth2Resource.class);


    @RequestMapping("/oauth2")
    public String oauth2ResourceController(HttpServletRequest request, HttpServletResponse response, Model model) throws MalformedURLException {
        String code = request.getParameter("code");
        String state = request.getParameter("state");
        log.trace("oauth2 got code: {}",code);
        log.trace("oauth2 - got state: {}",state);

        //String redirectURI = "http://www.vg.no";
        String token = new CommandVerifyToken(Configuration.getString("oauth.uri"), code).execute();
        log.trace("oauth2 got token: {}", token);
        return "action";
    }

}
