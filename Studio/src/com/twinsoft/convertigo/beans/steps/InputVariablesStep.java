package com.twinsoft.convertigo.beans.steps;

import java.util.HashMap;
import java.util.Map;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.twinsoft.convertigo.beans.common.XMLVector;
import com.twinsoft.convertigo.beans.core.Step;
import com.twinsoft.convertigo.beans.core.StepSource;
import com.twinsoft.convertigo.beans.variables.RequestableVariable;
import com.twinsoft.convertigo.engine.Engine;
import com.twinsoft.convertigo.engine.EngineException;
import com.twinsoft.convertigo.engine.util.GenericUtils;
import com.twinsoft.convertigo.engine.util.XMLUtils;

public class InputVariablesStep extends Step {

	private static final long serialVersionUID = 3276050659362959158L;

	protected String nodeName = "inputVars";

	transient Map<String, Object> variables = new HashMap<String, Object>();

	public InputVariablesStep() throws EngineException {
		super();
		this.output = true;
		this.xml = true;
	}

	public Object clone() throws CloneNotSupportedException {
		InputVariablesStep clonedObject = (InputVariablesStep) super.clone();
		return clonedObject;
	}

	public Object copy() throws CloneNotSupportedException {
		InputVariablesStep copiedObject = (InputVariablesStep) super.copy();
		return copiedObject;
	}

	public String toString() {
		String text = this.getComment();
		String tag = "<" + nodeName + ">";
		return tag + (!text.equals("") ? " // " + text : "");
	}

	protected boolean workOnSource() {
		return false;
	}

	protected StepSource getSource() {
		return null;
	}

	public String getNodeName() {
		return nodeName;
	}

	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	public String getStepNodeName() {
		return getNodeName();
	}

	@Override
	public String toJsString() {
		return "";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.twinsoft.convertigo.beans.core.Step#createWsdlDom()
	 */
	@Override
	protected Node createWsdlDom() throws EngineException {
		Element element = (Element) super.createWsdlDom();
		for (RequestableVariable rv : getParentSequence().getAllVariables()) {
			Element rvel = wsdlDom.createElement(rv.getName());
			element.appendChild(rvel);
		}
		return element;
	}

	@Override
	public String getSchemaType(String tns) {
		return tns + ":" + getStepNodeName() + priority + "StepType";
	}

	@Override
	public String getSchema(String tns, String occurs) throws EngineException {
		schema = "";
		String maxOccurs = (occurs == null) ? "" : "maxOccurs=\"" + occurs + "\"";
		schema += "\t\t\t<xsd:element minOccurs=\"0\" " + maxOccurs + " name=\"" + getStepNodeName()
				+ "\" type=\"" + getSchemaType(tns) + "\">\n";
		schema += "\t\t\t\t<xsd:annotation>\n";
		schema += "\t\t\t\t\t<xsd:documentation>" + XMLUtils.getCDataXml(getComment())
				+ "</xsd:documentation>\n";
		schema += "\t\t\t\t</xsd:annotation>\n";
		schema += "\t\t\t</xsd:element>\n";

		return isEnable() && isOutput() ? schema : "";
	}

	@Override
	public void addSchemaType(HashMap<Long, String> stepTypes, String tns, String occurs)
			throws EngineException {
		String maxOccurs, stepTypeSchema = "";
		stepTypeSchema += "\t<xsd:complexType name=\"" + getSchemaTypeName(tns) + "\">\n";
		stepTypeSchema += "\t\t<xsd:sequence>\n";
		for (RequestableVariable rv : getParentSequence().getAllVariables()) {
			maxOccurs = rv.isMultiValued() ? "maxOccurs=\"unbounded\"" : "";
			stepTypeSchema += "\t\t\t<xsd:element minOccurs=\"0\" " + maxOccurs + " name=\"" + rv.getName()
					+ "\" type=\"" + rv.getSchemaType() + "\" />\n";
		}
		stepTypeSchema += "\t\t</xsd:sequence>\n";
		stepTypeSchema += "\t</xsd:complexType>\n";

		stepTypes.put(new Long(priority), stepTypeSchema);
	}

	@Override
	protected boolean stepExecute(Context javascriptContext, Scriptable scope) throws EngineException {
		variables.clear();

		if (isEnable) {
			for (RequestableVariable var : getParentSequence().getAllVariables()) {
				try {
					evaluate(javascriptContext, scope, var.getName(), "expression", true);
					if (evaluated != null && !(evaluated instanceof Undefined)) {

						if (evaluated instanceof NativeJavaArray) {
							NativeJavaArray jsArray = (NativeJavaArray) evaluated;
							Object javaArray = jsArray.unwrap();

							if (javaArray.getClass().isArray()) {
								String[] objects = (String[]) javaArray;
								variables.put(var.getName(), objects);
							}
						} else {
							variables.put(var.getName(), evaluated);
						}
					}
				} catch (Exception e) {
					evaluated = null;
					Engine.logBeans.warn(e.getMessage());
				}
			}
			return super.stepExecute(javascriptContext, scope);
		}
		return false;
	}

	protected void createStepNodeValue(Document doc, Element stepNode) throws EngineException {
		for (Map.Entry<String, Object> entry : variables.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();

			if (value instanceof XMLVector) {
				XMLVector<Object> nodeValues = GenericUtils.cast(value);

				for (Object object : nodeValues) {
					Element var = doc.createElement(key.toString());
					Node text = doc.createTextNode(object.toString());
					var.appendChild(text);
					stepNode.appendChild(var);
				}
			} else if (value instanceof NativeJavaObject) {
				Element var = doc.createElement(key.toString());
				stepNode.appendChild(var);

				Object object = ((NativeJavaObject) value).unwrap();

				// Structured variable
				if (object instanceof NodeList) {
					NodeList valueNodeList = (NodeList) object;
					int nlLen = valueNodeList.getLength();
					Document document = stepNode.getOwnerDocument();
					for (int i = 0; i < nlLen; i++) {
						Node nodeVarPart = document.importNode((Node) valueNodeList.item(i), true);
						var.appendChild(nodeVarPart);
					}
				}
			} else if (value instanceof String[]) {
				String[] sNodevalues = GenericUtils.cast(value);
				for (Object object : sNodevalues) {
					Element var = doc.createElement(key.toString());
					Text text = doc.createTextNode(object.toString());
					var.appendChild(text);
					stepNode.appendChild(var);
				}
			} else {
				String nodeValue = value.toString();
				Element var = doc.createElement(key.toString());
				Node text = doc.createTextNode(nodeValue);
				var.appendChild(text);
				stepNode.appendChild(var);
			}
		}
	}
}