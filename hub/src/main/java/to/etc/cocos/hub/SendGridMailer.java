package to.etc.cocos.hub;

import com.sendgrid.Attachments;
import com.sendgrid.ClickTrackingSetting;
import com.sendgrid.Content;
import com.sendgrid.Email;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.TrackingSettings;
import to.etc.smtp.Address;
import to.etc.smtp.IMailAttachment;
import to.etc.smtp.Message;
import to.etc.util.FileTool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-3-19.
 */
public class SendGridMailer {
	private final String m_apiKey;

	private final String m_defaultFrom;

	public SendGridMailer(String apiKey, String defaultFrom) {
		m_apiKey = apiKey;
		m_defaultFrom = defaultFrom;
	}

	public void send(Message mb) throws Exception {
		Address from = mb.getFrom();
		Email fr;
		if(null == from) {
			fr = new Email(m_defaultFrom);
		} else {
			fr = new Email(from.getEmail(), from.getName());
		}
		var attachments = new ArrayList<Attachments>();
		for(IMailAttachment iMailAttachment : mb.getAttachmentList()) {
			Attachments attachments3 = new Attachments();
			byte[] attachmentContentBytes = FileTool.readByteArray(iMailAttachment.getInputStream());
			String attachmentContent = Base64.getEncoder().encodeToString(attachmentContentBytes);
			attachments3.setContent(attachmentContent);
			attachments3.setType(iMailAttachment.getMime());
			attachments3.setFilename(iMailAttachment.getIdent());
			attachments3.setDisposition("attachment");
			attachments3.setContentId(iMailAttachment.getIdent());
			attachments.add(attachments3);
		}


		String subject = mb.getSubject();

		for(Address t : mb.getTo()) {
			Email to = new Email(t.getEmail(), t.getName());
			Content content = new Content("text/plain", mb.getBody());
			Mail mail = new Mail(fr, subject, to, content);
			Content html = new Content("text/html", mb.getHtmlBody());
			mail.addContent(html);
			for(Attachments attachment : attachments) {
				mail.addAttachments(attachment);
			}
			TrackingSettings trackingSettings = new TrackingSettings();
			ClickTrackingSetting cts = new ClickTrackingSetting();
			trackingSettings.setClickTrackingSetting(cts);
			cts.setEnable(false);
			cts.setEnableText(false);
			mail.setTrackingSettings(trackingSettings);

			SendGrid sg = new SendGrid(m_apiKey);

			Request request = new Request();
			try {
				request.setMethod(Method.POST);
				request.setEndpoint("mail/send");
				request.setBody(mail.build());
				Response response = sg.api(request);
				System.out.println(response.getStatusCode());
				System.out.println(response.getBody());
				System.out.println(response.getHeaders());
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
}
