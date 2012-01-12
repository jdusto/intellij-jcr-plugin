package velir.intellij.cq5.jcr.model;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import velir.intellij.cq5.ui.RegexTextField;
import velir.intellij.cq5.util.Anonymous;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

public class VNode {

	private static final String BOOLEAN_PREFIX = "{Boolean}";
	private static final String DATE_PREFIX = "{Date}";
	private static final String DOUBLE_PREFIX = "{Double}";
	private static final String LONG_PREFIX = "{Long}";
	private static final String NAME_PREFIX = "{Name}";
	private static final String PATH_PREFIX = "{Path}";
	private static final String BINARY_PREFIX = "{Binary}";
	private static final String[] TYPESTRINGS = {
			"{String}",
			BOOLEAN_PREFIX,
			//DATE_PREFIX,
			DOUBLE_PREFIX,
			//NAME_PREFIX,
			//PATH_PREFIX,
			//BINARY_PREFIX,
			LONG_PREFIX,
			LONG_PREFIX + "[]"
	};

	private String name;
	private Map<String, Object> properties;
	private static final Logger log = LoggerFactory.getLogger(VNode.class);

	public static Map<String,Namespace> namespaces;
	static {
		namespaces = new HashMap<String, Namespace>();
		namespaces.put("cq", Namespace.getNamespace("cq","http://www.day.com/jcr/cq/1.0"));
		namespaces.put("jcr", Namespace.getNamespace("jcr","http://www.jcp.org/jcr/1.0"));
	}

	public VNode (String name, String type) {
		this.name = name;
		properties = new HashMap<String, Object>();
		properties.put("jcr:primaryType", type);
	}

	protected void setProperty (String name, Object value) {
		properties.put(name, value);
	}

	protected String getPropertyString (String name) {
		return (String) properties.get(name);
	}

	protected Boolean getPropertyBoolean (String name) {
		return (Boolean) properties.get(name);
	}

	protected Long getPropertyLong (String name) {
		return (Long) properties.get(name);
	}

	protected <T> T getProperty (String name, Class<T> type) {
		return (T) properties.get(name);
	}

	protected void removeProperty (String name) {
		properties.remove(name);
	}

	public String getName () {
		return name;
	}

	protected void setName (String name) {
		this.name = name;
	}

	private String getStringValue (Object o) {

		if (o instanceof Long) {
			return LONG_PREFIX + o.toString();
		} else if (o instanceof Boolean) {
			return BOOLEAN_PREFIX + o.toString();
		} else if (o instanceof Double) {
			return DOUBLE_PREFIX + o.toString();
		} else if (o instanceof Long[]) {
			String s = LONG_PREFIX + "[";
			Long[] ls = (Long[]) o;
			if (ls.length == 0) return s + "]";
			for (int i = 0; i < ls.length - 1; i++) {
				s += ls[i].toString() + ",";
			}
			return s + ls[ls.length - 1] + "]";
		} else if (o instanceof Boolean[]) {
			String s = BOOLEAN_PREFIX + "[";
			Boolean[] ls = (Boolean[]) o;
			if (ls.length == 0) return s + "]";
			for (int i = 0; i < ls.length - 1; i++) {
				s += ls[i].toString() + ",";
			}
			return s + ls[ls.length - 1] + "]";
		} else if (o instanceof Double[]) {
			String s = DOUBLE_PREFIX + "[";
			Double[] ls = (Double[]) o;
			if (ls.length == 0) return s + "]";
			for (int i = 0; i < ls.length - 1; i++) {
				s += ls[i].toString() + ",";
			}
			return s + ls[ls.length - 1] + "]";
		} else {
			return o.toString();
		}
	}

	public Element getElement() {
		Element element = new Element(name);
		Set<String> elementNamespaces = new HashSet<String>();

		// properties
		for (Map.Entry<String,Object> property : properties.entrySet()) {

			// get namespace from property string, if there
			Namespace propertyNamespace = null;
			String propertyName = property.getKey();
			String[] attributeSections = propertyName.split(":");
			// if namespaced property
			if (attributeSections.length == 2) {
				propertyNamespace = namespaces.get(attributeSections[0]);
				if (propertyNamespace == null) {
					log.error("No namespace definition found for property: " + property.getKey());
				}
				else {
					propertyName = attributeSections[1];
					// add namespace to element if it isn't there already
					if (!elementNamespaces.contains(attributeSections[0])) {
						element.addNamespaceDeclaration(propertyNamespace);
						elementNamespaces.add(attributeSections[0]);
					}
				}
			}

			// prepend string value with property type
			Object value = property.getValue();
			String propertyStringValue = getStringValue(value);

			// set property
			if (propertyNamespace != null) {
				// propertyName cannot have colon, even here
				element.setAttribute(propertyName, propertyStringValue, propertyNamespace);
			}
			else {
				element.setAttribute(propertyName, propertyStringValue);
			}
		}

		return element;
	}

	interface Callback<T> {
		public void process(T t);
	}

	private void addPropertyPanel (final JPanel parentPanel, final String name, final Object value) {
		// convenience class for adding document listeners
		class DocumentListenerAdder {
			public DocumentListenerAdder (final JTextField jTextField, final Callback<String> callback) {
				jTextField.getDocument().addDocumentListener(new DocumentListener() {
					public void insertUpdate(DocumentEvent e) {
						callback.process(jTextField.getText());
					}

					public void removeUpdate(DocumentEvent e) {
						callback.process(jTextField.getText());
					}

					public void changedUpdate(DocumentEvent e) {
						callback.process(jTextField.getText());
					}
				});
			}
		}

		// convenience class for adding single-valued document listeners
		class DocumentListenerSingleAdder {
			public DocumentListenerSingleAdder (final String name, final JTextField jTextField, final Anonymous<String, Object> makeObject) {
				new DocumentListenerAdder(jTextField, new Callback<String>() {
					public void process(String s) {
						setProperty(name, makeObject.call(s));
					}
				});
			}
		}

		final JPanel jPanel = new JPanel(new GridLayout(1,3));

		// make sure the property is set in the node
		setProperty(name, value);

		// make label
		JLabel jLabel = new JLabel(name);
		jPanel.add(jLabel);

		// make input based on property class
		if (value instanceof Boolean) {
			final JCheckBox jCheckBox = new JCheckBox("", (Boolean) value);
			jCheckBox.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					setProperty(name, jCheckBox.isSelected());
				}
			});
			jPanel.add(jCheckBox);
		} else if (value instanceof Double) {
			final RegexTextField regexTextField = new RegexTextField(Pattern.compile("[0-9]*\\.?[0-9]*"), value.toString());
			new DocumentListenerSingleAdder(name,regexTextField,new Anonymous<String, Object>() {
				public Object call(String s) {
					return Double.parseDouble(s);
				}
			});
			jPanel.add(regexTextField);
		} else if (value instanceof Long) {
			final RegexTextField regexTextField = new RegexTextField(Pattern.compile("[0-9]*"), value.toString());
			new DocumentListenerSingleAdder(name, regexTextField, new Anonymous<String, Object>() {
				public Object call(String s) {
					return Long.parseLong(s);
				}
			});
			jPanel.add(regexTextField);
		} else if (value instanceof Long[]) {
			final Set<RegexTextField> inputs = new HashSet<RegexTextField>();
			final JPanel valuesPanel = new JPanel(new VerticalFlowLayout());

			final Runnable setPropertyValues = new Runnable() {
				public void run() {
					Long[] values = new Long[inputs.size()];
					int i = 0;
					for (RegexTextField input : inputs) {
						values[i++] = Long.parseLong(input.getText());
					}
					setProperty(name, values);
				}
			};

			final Callback<Long> addValuePanel = new Callback<Long>() {
				public void process(Long aLong) {
					final JPanel innerPanel = new JPanel(new GridLayout(1,2));

					final RegexTextField regexTextField = new RegexTextField(Pattern.compile("[0-9]*"), aLong.toString());
					new DocumentListenerAdder(regexTextField, new Callback<String>() {
						public void process(String s) {
							setPropertyValues.run();
						}
					});
					inputs.add(regexTextField);
					innerPanel.add(regexTextField);

					// remove value button
					JButton jButton = new JButton("X");
					jButton.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							inputs.remove(regexTextField);
							valuesPanel.remove(innerPanel);
							valuesPanel.revalidate();

							// set property now that value has been removed
							setPropertyValues.run();
						}
					});
					innerPanel.add(jButton);

					valuesPanel.add(innerPanel);
				}
			};

			// add a panel for each value of values array
			for (Long lo : (Long[]) value ) {
				addValuePanel.process(lo);
			}

			// add new value button
			JButton jButton = new JButton("Add");
			jButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					addValuePanel.process(0L);
					setPropertyValues.run();
					valuesPanel.revalidate();
				}
			});
			valuesPanel.add(jButton);

			jPanel.add(valuesPanel);
		} else {
			final JTextField jTextField = new JTextField(value.toString());
			new DocumentListenerSingleAdder(name, jTextField, new Anonymous<String, Object>() {
				public Object call(String s) {
					return s;
				}
			});
			jPanel.add(jTextField);
		}

		// make remove button
		JButton jButton = new JButton("remove");
		jButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				parentPanel.remove(jPanel);
				parentPanel.revalidate();
				removeProperty(name);
			}
		});
		jPanel.add(jButton);

		parentPanel.add(jPanel);
	}

	public JPanel makePanel (boolean nameEditingEnabled) {
		JPanel nodePanel = new JPanel(new VerticalFlowLayout());

		// node name
		JPanel namePanel = new JPanel(new GridLayout(1,2));
		JLabel nameLabel = new JLabel("name");
		namePanel.add(nameLabel);
		final JTextField nameField = new JTextField(name);
		nameField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				name = nameField.getText();
			}

			public void removeUpdate(DocumentEvent e) {
				name = nameField.getText();
			}

			public void changedUpdate(DocumentEvent e) {
				name = nameField.getText();
			}
		});
		nameField.setEditable(nameEditingEnabled);
		namePanel.add(nameField);
		nodePanel.add(namePanel);

		// separator
		nodePanel.add(new JSeparator(JSeparator.HORIZONTAL));

		// properties
		final JPanel propertiesPanel = new JPanel(new VerticalFlowLayout());
		for (Map.Entry<String,Object> property : properties.entrySet()) {
			addPropertyPanel(propertiesPanel, property.getKey(), property.getValue());
		}
		nodePanel.add(propertiesPanel);

		// separator
		nodePanel.add(new JSeparator(JSeparator.HORIZONTAL));

		// make add property panel
		JPanel newPropertyPanel = new JPanel(new GridLayout(1,2));
		final JTextField jTextField = new JTextField();
		newPropertyPanel.add(jTextField);
		final JComboBox jComboBox = new JComboBox(TYPESTRINGS);
		newPropertyPanel.add(jComboBox);
		JButton jButton = new JButton("add property");
		jButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String type = (String) jComboBox.getSelectedItem();
				if (BOOLEAN_PREFIX.equals(type)) {
					addPropertyPanel(propertiesPanel, jTextField.getText(), false);
				} else if (LONG_PREFIX.equals(type)) {
					addPropertyPanel(propertiesPanel, jTextField.getText(), 0L);
				} else if (DOUBLE_PREFIX.equals(type)) {
					addPropertyPanel(propertiesPanel, jTextField.getText(), 0.0D);
				} else if ((LONG_PREFIX + "[]").equals(type)) {
					addPropertyPanel(propertiesPanel, jTextField.getText(), new Long[] {0L});
				} else {
					addPropertyPanel(propertiesPanel, jTextField.getText(), "");
				}
				propertiesPanel.revalidate();
			}
		});
		newPropertyPanel.add(jButton);
		nodePanel.add(newPropertyPanel);

		return nodePanel;
	}


	public static VNode makeVNode (InputStream inputStream, String name) {
		VNode vNode = null;
		try {
			Document document = JDOMUtil.loadDocument(inputStream);
			final Element element = document.getRootElement();

			vNode = new VNode(name, "_dummy_");

			for (Object o : element.getAttributes()) {
				Attribute attribute = (Attribute) o;

				String propertyName = attribute.getQualifiedName();
				String value = attribute.getValue();

				// choose which type of object to insert
				if (value.startsWith(BOOLEAN_PREFIX)) {
					Boolean b = Boolean.parseBoolean(value.replaceFirst(Pattern.quote(BOOLEAN_PREFIX), ""));
					vNode.setProperty(propertyName, b);
				} else if (value.startsWith(DOUBLE_PREFIX)) {
					Double d = Double.parseDouble(value.replaceFirst(Pattern.quote(DOUBLE_PREFIX), ""));
					vNode.setProperty(propertyName, d);
				} else if (value.startsWith(LONG_PREFIX + "[")) {
					Long[] vals;
					String valuesString = value.substring(0, value.length() - 1).replaceFirst(Pattern.quote(LONG_PREFIX + "["), "");
					if ("".equals(valuesString)) vals = new Long[0];
					else {
						String[] valueBits = valuesString.split(",");
						vals = new Long[valueBits.length];
						for (int i = 0; i < valueBits.length; i++) {
							vals[i] = Long.parseLong(valueBits[i]);
						}
					}
					vNode.setProperty(propertyName, vals);
				} else if (value.startsWith(LONG_PREFIX)) {
					Long l = Long.parseLong(value.replaceFirst(Pattern.quote(LONG_PREFIX), ""));
					vNode.setProperty(propertyName, l);
				} else {
					vNode.setProperty(propertyName, value);
				}
			}

		} catch (JDOMException jde) {
			log.error("Could not load VNode from inputstream", jde);

		} catch (IOException ioe) {
			log.error("Could not load VNode from inputstream", ioe);
		}

		return vNode;
	}

}
