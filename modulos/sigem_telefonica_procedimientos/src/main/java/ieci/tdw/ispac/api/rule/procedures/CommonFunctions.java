package ieci.tdw.ispac.api.rule.procedures;

import ieci.tdw.ispac.api.IEntitiesAPI;
import ieci.tdw.ispac.api.IGenDocAPI;
import ieci.tdw.ispac.api.IProcedureAPI;
import ieci.tdw.ispac.api.errors.ISPACException;
import ieci.tdw.ispac.api.errors.ISPACInfo;
import ieci.tdw.ispac.api.item.IItem;
import ieci.tdw.ispac.api.item.IItemCollection;
import ieci.tdw.ispac.api.rule.IRuleContext;
import ieci.tdw.ispac.ispaclib.context.IClientContext;
import ieci.tdw.ispac.ispaclib.gendoc.openoffice.OpenOfficeHelper;
import ieci.tdw.ispac.ispaclib.util.FileTemporaryManager;
import ieci.tdw.ispac.ispaclib.utils.MimetypeMapping;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Random;

import org.apache.log4j.Logger;

import com.sun.star.beans.XPropertySet;
import com.sun.star.connection.NoConnectException;
import com.sun.star.container.XNameAccess;
import com.sun.star.container.XNamed;
import com.sun.star.document.XDocumentInsertable;
import com.sun.star.frame.XController;
import com.sun.star.frame.XModel;
import com.sun.star.lang.DisposedException;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.style.ParagraphAdjust;
import com.sun.star.text.XBookmarksSupplier;
import com.sun.star.text.XPageCursor;
import com.sun.star.text.XText;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextViewCursor;
import com.sun.star.text.XTextViewCursorSupplier;
import com.sun.star.uno.UnoRuntime;

public class CommonFunctions 
{
	/** Logger de la clase. */
	protected static final Logger logger = Logger.getLogger(CommonFunctions.class);
	
	/**
	 * Devuelve Infopag, la referencia al Repositorio documental de un documento.
	 *
	 * @param rulectx El contexto de la regla.
	 * @param descripcion Campo 'Descripcion' en Spac_dt_documentos con el que localizar al documento.
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */	
	public static String getInfoPag(IRuleContext rulectx, String descripcion) throws ISPACException
	{
		String strInfoPag = "";
		
		try
		{
			IEntitiesAPI entitiesAPI = rulectx.getClientContext().getAPI().getEntitiesAPI();
			String strQuery = "WHERE NUMEXP = '" + rulectx.getNumExp() + "' AND DESCRIPCION = '" + descripcion + "' ORDER BY ID DESC";
	        IItemCollection collection = entitiesAPI.queryEntities("SPAC_DT_DOCUMENTOS", strQuery);
	        Iterator it = collection.iterator();
	        if (it.hasNext())
	        {
	        	IItem doc = (IItem)it.next();
	        	strInfoPag = doc.getString("INFOPAG");
	        }
			
		}
		catch(Exception e)
		{
			throw new ISPACException(e); 	
		}
		return strInfoPag;
	}
	
	/**
	 * Devuelve Infopag, la referencia al Repositorio documental de un documento.
	 *
	 * @param rulectx El contexto de la regla.
	 * @param nombre Campo 'Nombre' en Spac_dt_documentos con el que localizar al documento.
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */	
	public static String getInfoPagNombre(IRuleContext rulectx, String nombre) throws ISPACException
	{
		String strInfoPag = "";
		
		try
		{
			IEntitiesAPI entitiesAPI = rulectx.getClientContext().getAPI().getEntitiesAPI();
			String strQuery = "WHERE NUMEXP = '" + rulectx.getNumExp() + "' AND NOMBRE = '" + nombre + "' ORDER BY ID DESC";
	        IItemCollection collection = entitiesAPI.queryEntities("SPAC_DT_DOCUMENTOS", strQuery);
	        Iterator it = collection.iterator();
	        if (it.hasNext())
	        {
	        	IItem doc = (IItem)it.next();
	        	strInfoPag = doc.getString("INFOPAG");
	        }
			
		}
		catch(Exception e)
		{
			throw new ISPACException(e); 	
		}
		return strInfoPag;
	}
	
	/**
	 * Crea un fichero en el repositorio temporal como copia de un documento del repositorio documental.
	 *
	 * @param rulectx El contexto de la regla.
	 * @param strInfoPag InfoPag del documento a copiar.
	 * @return Objeto File con el fichero generado.
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */	
	public static File getFile(IRuleContext rulectx, String strInfoPag) throws ISPACException
	{
		File file = null;
		Object connectorSession = null;
	
		try
		{
			IGenDocAPI gendocAPI = rulectx.getClientContext().getAPI().getGenDocAPI();
			
			connectorSession = gendocAPI.createConnectorSession();

			//[eCenpri-Manu] Ticket #182 Modificamos la extensi�n para que recupere la propia del documento, y no asigne .doc a todos los documentos.
//			String extension = "doc";
			String extension = MimetypeMapping.getExtension(gendocAPI.getMimeType(connectorSession, strInfoPag));		
			//MQE Fin modficaciones ticket #182
				
			String fileName = FileTemporaryManager.getInstance().newFileName("."+extension);
			fileName = FileTemporaryManager.getInstance().getFileTemporaryPath() + "/" + fileName;
			
			OutputStream out = new FileOutputStream(fileName);
			gendocAPI.getDocument(connectorSession, strInfoPag, out);
			
			file = new File(fileName);
		}
		catch(Exception e)
		{
			throw new ISPACException(e); 	
		}
		return file;
	}
	
	/**
	 * Concatena el contenido de un fichero Word a otro ya abierto previamente en OpenOffice.
	 *
	 * @param xComponent Fichero abierto en OpenOffice.
	 * @param file Path del fichero a concatenar.
	 * @param ooHelper Variable privada de clase OpenOfficeHelper.
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */	
	public static void Concatena(XComponent xComponent , String file, OpenOfficeHelper ooHelper)throws ISPACException
	{
		// La funci�n ooHelper.concatFiles mete saltos de p�gina cuando quiere. 
		// Yo quiero concatenar a continuaci�n as� que no la puedo usar y tengo
		// que hacer un apa�o a mano. M�s info en:
		//      http://www.oooforum.org/forum/viewtopic.phtml?t=28376
		//ooHelper.concatFiles(xComponent, file);
		try
		{
			//Obtengo el texto del documento y muevo el cursor al final del mismo
		    XTextDocument xTextDocument = (XTextDocument)UnoRuntime.queryInterface(XTextDocument.class, xComponent);
		    XText xText = xTextDocument.getText();
		    XTextCursor xTextCursor = xText.createTextCursor();
		    xTextCursor.gotoRange(xText.getEnd(),false);

		    //Marco el comienzo del nuevo documento con un bookmark
		    //Para anclar el bookmark uso un caracter, por ejemplo 'X'
		    xText.insertString(xTextCursor, "X", false);
		    xTextCursor.goLeft((short)1, false);
		    Random rand = new Random(); //No hace falta pero as� aseguro que no se repiten bookmarks
		    String bookmarkName = "dummyBookmark" + String.valueOf(rand.nextInt());
		    insertBookmark(xTextDocument, xTextCursor, bookmarkName);
		    xTextCursor.gotoRange(xText.getEnd(),false);
		    
		    //Inserto el documento. Esto a�adir� un salto de p�gina
		    XDocumentInsertable xDocInsert = (XDocumentInsertable)UnoRuntime.queryInterface(XDocumentInsertable.class, xTextCursor);
			xDocInsert.insertDocumentFromURL(file, null);
			//Esperamos un tiempo a que concluya la operaci�n
			//para evitar problemas de sincronizaci�n.
			//Thread.sleep(3000);
			
			//Recupero el bookmark y tomo su ancla, que se sit�a sobre el caracter 'X'
			XBookmarksSupplier xBookmarksSupplier = (XBookmarksSupplier)UnoRuntime.queryInterface(XBookmarksSupplier.class, xComponent);
			XNameAccess xNamedBookmarks = xBookmarksSupplier.getBookmarks();
			Object foundBookmark = xNamedBookmarks.getByName(bookmarkName);
			XTextContent xFoundBookmark = (XTextContent)UnoRuntime.queryInterface(XTextContent.class, foundBookmark);
			XTextRange xAncla = xFoundBookmark.getAnchor();
			
			//Coloco el ViewCursor en el ancla
			XModel xModel = (XModel)UnoRuntime.queryInterface(XModel.class, xComponent); 
			XController xController = xModel.getCurrentController(); 
			XTextViewCursorSupplier xViewCursorSupplier = (XTextViewCursorSupplier)UnoRuntime.queryInterface(XTextViewCursorSupplier.class, xController); 
			XTextViewCursor xViewCursor = xViewCursorSupplier.getViewCursor(); 
			xViewCursor.gotoRange(xAncla, false);
			
		    //Comienzo el nuevo documento en una l�nea nueva
			//Meto el salto de linea entre el salto de secci�n y el principio de la nueva secci�n
			//porque de este modo protejo la hoja de estilos del primer p�rrafo del documento
			xViewCursor.goRight((short)2,false);
			xText.insertControlCharacter(xViewCursor, com.sun.star.text.ControlCharacter.PARAGRAPH_BREAK, false);
			xViewCursor.goLeft((short)3,false);
			
			//Pulso 'delete' por medio de un dispatcher para borrar el salto de p�gina
			//El primer delete borra la 'X' y con ello el bookmark
			//El segundo delete borra el salto de p�gina
			//(el m�todo deletePageBreak en realidad no tiene nada que ver con saltos de p�gina)
			//ooHelper.deletePageBreak(xComponent, xViewCursor);
			//ooHelper.deletePageBreak(xComponent, xViewCursor);
			xViewCursor.goRight((short)2,true);
			xViewCursor.setString("");
//			logger.warn("************ Insertar J.");
//			xText.insertString(xViewCursor, "J", false);
			
			//Devuelvo el viewcursor al final del documento
			xTextCursor.gotoEnd(false);
			xViewCursor.gotoRange(xTextCursor, false);
		}
		catch(Exception e)
		{
			throw new ISPACException(e);
		}
	}
	
	private static void insertBookmark(XTextDocument xTextDocument, XTextRange xTextRange, String name)throws ISPACException
	{
		try
		{
			//Creo un bookmark vacio
			XMultiServiceFactory xDocMSF = (XMultiServiceFactory) UnoRuntime.queryInterface(XMultiServiceFactory.class, xTextDocument);
			Object xObject = xDocMSF.createInstance("com.sun.star.text.Bookmark");

			//Asigno el nombre al bookmark
			XNamed xNameAccess = (XNamed) UnoRuntime.queryInterface(XNamed.class, xObject);
			xNameAccess.setName(name);

			//Inserto el bookmark en el sitio indicado
			XTextContent xTextContent = (XTextContent) UnoRuntime.queryInterface(XTextContent.class, xNameAccess);
			xTextDocument.getText().insertTextContent(xTextRange, xTextContent, false);
		}
		catch(Exception e)
		{
			throw new ISPACException(e);
		}
	}

	/**
	 * Inserta un salto de p�gina en un fichero Word abierto previamente en OpenOffice.
	 *
	 * @param xComponent Fichero abierto en OpenOffice.
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */	
	public static void insertaSaltoDePagina(XComponent xComponent) throws ISPACException
	{
		//No se puede insertar un salto de p�gina directamente.
		//Los saltos de p�gina son una propiedad de los p�rrafos
		//As� que inserto un nuevo p�rrafo y luego le ajusto la propiedad
		
		try
		{
		    XTextDocument xTextDocument = (XTextDocument)UnoRuntime.queryInterface(XTextDocument.class, xComponent);
		    XText xText = xTextDocument.getText();
		    XTextCursor xTextCursor = xText.createTextCursor();
		    xTextCursor.gotoRange(xText.getEnd(),false);

		    // Inserto un salto de l�nea (nuevo p�rrafo)
			xText.insertControlCharacter(xTextCursor, com.sun.star.text.ControlCharacter.PARAGRAPH_BREAK, false);

			//Le digo a este nuevo p�rrafo que comience con salto de p�gina
			XPropertySet xProps = (XPropertySet) UnoRuntime.queryInterface(
			XPropertySet.class, xTextCursor);
			xProps.setPropertyValue("BreakType", new Integer(com.sun.star.style.BreakType.PAGE_BEFORE_value));
		}
		catch(Exception e)
		{
			throw new ISPACException(e);
		}
	}
	
	/**
	 * Predicado que indica si se ha ejecutado previamente un cierto tr�mite
	 *
	 * @param rulectx El contexto de la regla.
	 * @param tramite Nombre del tr�mite.
	 * @return Devuelve 'true' si ya exist�a con anterioridad otro tr�mite con el nombre 'tramite'
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */	
	public static boolean existeTramite(IRuleContext rulectx, String tramite) throws ISPACException
	{
		boolean existe = false;
		try
		{
			IClientContext cct = rulectx.getClientContext();
			IEntitiesAPI entitiesAPI = cct.getAPI().getEntitiesAPI();
			String strQuery = "WHERE NUMEXP = '" + rulectx.getNumExp() + "' AND NOMBRE = '" + tramite + "'";
	        IItemCollection collection = entitiesAPI.queryEntities("SPAC_DT_TRAMITES", strQuery);
	        Iterator it = collection.iterator();
	        if (it.hasNext())
	        {
	        	//Parece que existe pero puede ser este mismo tr�mite
	        	String strId = ((IItem)it.next()).getString("ID");
	        	int id = Integer.parseInt(strId);
	        	existe = id != rulectx.getTaskId();
	        }
		}
		catch(Exception e)
		{
			throw new ISPACException(e); 	
		}
		
		return existe;
	}

	/**
	 * Devuelve el nombre del Tipo de Documento asociado a un cierto c�digo
	 *
	 * @param rulectx El contexto de la regla.
	 * @param strCodTpDoc C�digo de Tipo de Documento.
	 * @return Nombre del Tipo de Documento asociado a strCodTpDoc
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */	
	public static String getNombreTpDoc(IRuleContext rulectx, String strCodTpDoc) throws ISPACException
	{
		String strNombreTramite = "";
		try
		{
	        IEntitiesAPI entitiesAPI = rulectx.getClientContext().getAPI().getEntitiesAPI();
			String strQuery = "WHERE COD_TPDOC='"+strCodTpDoc+"'";
			IItemCollection tpdocs = entitiesAPI.queryEntities("SPAC_CT_TPDOC", strQuery);
			Iterator it = tpdocs.iterator();
			if (it.hasNext())
			{
				IItem tpdoc= (IItem)it.next();
				strNombreTramite = tpdoc.getString("NOMBRE");
			}
		}
		catch (Exception e)
		{
        	throw new ISPACException(e);
		}
		return strNombreTramite;
	}

	/**
	 * Devuelve el Id de una entidad a partir de su nombre 
	 *
	 * @param rulectx El contexto de la regla.
	 * @param strEntity Nombre de la entidad.
	 * @return Campo Id de la tabla de entidades del Cat�logo
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */	
	public static int getEntityId(IRuleContext rulectx, String strEntity) throws ISPACException
	{
		int id = -1;
		try
		{
	        IEntitiesAPI entitiesAPI = rulectx.getClientContext().getAPI().getEntitiesAPI();
			String strQuery = "WHERE NOMBRE='"+strEntity+"'";
			IItemCollection entidades = entitiesAPI.queryEntities("SPAC_CT_ENTIDADES", strQuery);
			Iterator it = entidades.iterator();
			if (it.hasNext())
			{
				IItem entidad = (IItem)it.next();
				id = entidad.getInt("ID");
			}
		}
		catch (Exception e)
		{
        	throw new ISPACException(e);
		}
		return id;
	}
	
	/**
	 * Genera un documento a partir de una plantilla.
	 * La funcionalidad es la misma que si se hiciese desde el enlace
	 * de la aplicaci�n adjuntar documento 'Desde plantilla'. 
	 *
	 * @param rulectx El contexto de la regla.
	 * @param nombreTpDoc Nombre del tipo de documento.
	 * @param nombrePlantilla Nombre de la plantilla.
	 * @param descripcion Cadena de texto opcional. Si no es 'null' se a�ade al campo 'descripcion' del documento generado, separada tras un gui�n " - ".
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */	
	public static void generarDocumento(IRuleContext rulectx, String nombreTpDoc, String nombrePlantilla, String descripcion) throws ISPACException
	{
		try
		{
			//APIs
			IClientContext cct = rulectx.getClientContext();
			IEntitiesAPI entitiesAPI = cct.getAPI().getEntitiesAPI();
			IGenDocAPI gendocAPI = cct.getAPI().getGenDocAPI();
			IProcedureAPI procedureAPI = cct.getAPI().getProcedureAPI();

			//Obtenci�n de los tipos de documento asociados al tr�mite
			IItem processTask =  entitiesAPI.getTask(rulectx.getTaskId());
			int idTramCtl = processTask.getInt("ID_TRAM_CTL");
	    	IItemCollection taskTpDocCollection = (IItemCollection)procedureAPI.getTaskTpDoc(idTramCtl);
	    	if(taskTpDocCollection==null || taskTpDocCollection.toList().isEmpty())
	    	{
	    		throw new ISPACInfo("No hay ning�n tipo de documento asociado al tr�mite");
	    	}

    		//B�squeda de la plantilla indicada
	    	//  Primero busco el tipo de documento
        	Iterator itTpDocs = taskTpDocCollection.iterator();
        	boolean found = false;
        	while(itTpDocs.hasNext())
        	{
		    	IItem taskTpDoc = (IItem)itTpDocs.next();
	    		int documentTypeId = taskTpDoc.getInt("TASKTPDOC:ID_TPDOC");
	        	int tpDocId = getIdTpDoc(rulectx, nombreTpDoc);
	    		if (tpDocId != documentTypeId)
	    		{
	    			//Este no es el Tipo de documento solicitado
	    			continue;
	    		}

	    		//Ahora busco la plantilla indicada
	        	IItemCollection tpDocsTemplatesCollection = (IItemCollection)procedureAPI.getTpDocsTemplates(documentTypeId);
	        	if(tpDocsTemplatesCollection==null || tpDocsTemplatesCollection.toList().isEmpty())
	        	{
	        		//No hay ninguna plantilla asociada al tipo de documento
	        		continue;
	        	}
	        	Iterator itTemplate = tpDocsTemplatesCollection.iterator();
	        	while(itTemplate.hasNext() && !found)
	        	{
		    		IItem tpDocsTemplate = (IItem)itTemplate.next();
		        	int templateId = tpDocsTemplate.getInt("ID");
	
		        	String strTemplateName = tpDocsTemplate.getString("NOMBRE");
		        	if (strTemplateName.compareTo(nombrePlantilla)==0)
		        	{
		        		//Plantilla encontrada. Genero el documento
		        		found = true;
		        		Object connectorSession = null;
		        		try
		        		{
							connectorSession = gendocAPI.createConnectorSession();
							// Abrir transacci�n para que no se pueda generar un documento sin fichero
					        cct.beginTX();
						
							int taskId = rulectx.getTaskId();
				        	IItem entityDocument = gendocAPI.createTaskDocument(taskId, documentTypeId);
							int documentId = entityDocument.getKeyInt();
			
							// Generar el documento a partir de la plantilla
							IItem entityTemplate = gendocAPI.attachTaskTemplate(connectorSession, taskId, documentId, templateId);
							entityTemplate.set("EXTENSION", "doc");
							if ( descripcion != null)
							{
								String templateDescripcion = entityTemplate.getString("DESCRIPCION");
								templateDescripcion = templateDescripcion + " - " + descripcion;
								entityTemplate.set("DESCRIPCION", templateDescripcion);
								entityTemplate.store(cct);
							}
							entityTemplate.store(cct);
						}
						catch (Throwable e)
						{
							// Si se produce alg�n error se hace rollback de la transacci�n
							cct.endTX(false);
							
							String message = "exception.documents.generate";
							String extraInfo = null;
							Throwable eCause = e.getCause();
							
							if (eCause instanceof ISPACException)
							{
								if (eCause.getCause() instanceof NoConnectException) 
								{
									extraInfo = "exception.extrainfo.documents.openoffice.off"; 
								}
								else
								{
									extraInfo = eCause.getCause().getMessage();
								}
							}
							else if (eCause instanceof DisposedException)
							{
								extraInfo = "exception.extrainfo.documents.openoffice.stop";
							}
							else
							{
								extraInfo = e.getMessage();
							}			
							throw new ISPACInfo(message, extraInfo);
							
						}
						finally
						{
							if (connectorSession != null)
							{
								gendocAPI.closeConnectorSession(connectorSession);
							}
						}
				    	
			        	// Si todo ha sido correcto se hace commit de la transacci�n
						cct.endTX(true);
		        	}
	        	}
	    	}
		}
		catch(Exception e)
		{
			throw new ISPACException(e);
		}
	}
	
	/**
	 * Devuelve el identificador del tipo de documento indicado
	 * 
	 * @param rulectx El contexto de la regla.
	 * @param nombreTpDoc Nombre del tipo de documento.
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */
	public static int getIdTpDoc(IRuleContext rulectx, String nombreTpDoc) throws ISPACException 
	{
		int idTpDoc = -1;
		try
		{
	        IEntitiesAPI entitiesAPI = rulectx.getClientContext().getAPI().getEntitiesAPI();
			String strQuery = "WHERE NOMBRE='"+nombreTpDoc+"'";
			IItemCollection tpDocs = entitiesAPI.queryEntities("SPAC_CT_TPDOC", strQuery);
			Iterator it = tpDocs.iterator();
			if (it.hasNext())
			{
				IItem tpdoc = (IItem)it.next();
				idTpDoc = tpdoc.getInt("ID");
			}
		}
		catch (Exception e)
		{
        	throw new ISPACException(e);
		}
		return idTpDoc;
	}

	/**
	 * Establece el n�mero de la primera p�gina de un documento
	 * 
	 * @param xComponent Fichero abierto en OpenOffice.
	 * @param numPag N�mero de la primera p�gina
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */
	public static void establecerNumeroPagina(XComponent xComponent, String numPag) throws ISPACException
	{
		try
		{
		    XTextDocument xTextDocument = (XTextDocument)UnoRuntime.queryInterface(XTextDocument.class, xComponent);
		    XText xText = xTextDocument.getText();
		    XTextCursor xTextCursor = xText.createTextCursor();
		    xTextCursor.gotoRange(xText.getStart(),false);

			// Ponemos el n�mero de p�gina
			XPropertySet xProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xTextCursor);
			xProps.setPropertyValue("PageNumberOffset", new Short(numPag));
		}
		catch(Exception e)
		{
			throw new ISPACException(e);
		}
	}
	
	/**
	 * Inserta al final del documento el texto indicado en negrita
	 * 
	 * @param xComponent Fichero abierto en OpenOffice.
	 * @param texto Texto que se quiere insertar
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */
	public static void insertaTextoNegrita(XComponent xComponent, String texto) throws ISPACException
	{
		try
		{
		    XTextDocument xTextDocument = (XTextDocument)UnoRuntime.queryInterface(XTextDocument.class, xComponent);
		    XText xText = xTextDocument.getText();
		    XTextCursor xTextCursor = xText.createTextCursor();
		    xTextCursor.gotoRange(xText.getEnd(),false);

		    // Insertamos un salto de l�nea
			xText.insertString(xTextCursor, "\r", false);

			// Ponemos el texto en negrita
			XPropertySet xProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xTextCursor);
    		xProps.setPropertyValue("ParaStyleName", "Standard");
    		xProps.setPropertyValue("ParaAdjust", ParagraphAdjust.LEFT);
			xProps.setPropertyValue("CharWeight", new Float(com.sun.star.awt.FontWeight.BOLD));
    		xProps.setPropertyValue("CharFontName", new String("Trebuchet MS"));
    		xProps.setPropertyValue("CharHeight", new Float(10.0));	

//			xText.insertString(xTextCursor, "\r" + texto, false);
			xText.insertString(xTextCursor, texto, false);

			// Quitamos las negritas
			xProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xTextCursor);
			xProps.setPropertyValue("CharWeight", new Float(com.sun.star.awt.FontWeight.NORMAL));
		}
		catch(Exception e)
		{
			throw new ISPACException(e);
		}
	}

	/**
	 * Devuelve el n�mero de caracteres que tiene el documento
	 * 
	 * @param xComponent Fichero abierto en OpenOffice.
	 * @return N�mero de caracteres del documento
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */
	public static Integer getNumeroCaracteres(XComponent xComponent) throws ISPACException
	{
		try
		{
		    XTextDocument xTextDocument = (XTextDocument)UnoRuntime.queryInterface(XTextDocument.class, xComponent);
		    XPropertySet propTextDocument = (XPropertySet)UnoRuntime.queryInterface(XPropertySet.class, xTextDocument);

		    logger.warn("N�mero de caracteres: " + propTextDocument.getPropertyValue("CharacterCount"));
//		    logger.warn("N�mero de palabras: " + propTextDocument.getPropertyValue("WordCount"));
		    return (Integer) propTextDocument.getPropertyValue("CharacterCount");
		}
		catch(Exception e)
		{
			throw new ISPACException(e);
		}
	}

	/**
	 * Insertar bookmark en el documento
	 * 
	 * @param xComponent Fichero abierto en OpenOffice.
	 * @param bookmark Marcador que se va a insertar.
	 * @throws ISPACException Debido a errores en la API de SIGEM.
	 */
	public static void InsertarBookmark(XComponent xComponent, String bookmark) throws ISPACException
	{
		try
		{
		    XTextDocument xTextDocument = (XTextDocument)UnoRuntime.queryInterface(XTextDocument.class, xComponent);
		    XText xText = xTextDocument.getText();
		    XTextCursor xTextCursor = xText.createTextCursor();
			XMultiServiceFactory xDocMSF = (XMultiServiceFactory) UnoRuntime.queryInterface(XMultiServiceFactory.class, xTextDocument);

		    //Empiezo por escribir un texto precedido de un marcador de referencia
			//--------------------------------------------------------------------

			//Coloco el texto al principio del documento
		    xTextCursor.gotoRange(xText.getEnd(),false);
		    
		    //Creo la referencia
		    XNamed xRefMark = (XNamed) UnoRuntime.queryInterface(XNamed.class, 
		    		xDocMSF.createInstance("com.sun.star.text.ReferenceMark"));
	        xRefMark.setName(bookmark);
	        
	        //Inserto la referencia
	        XTextContent xContent = (XTextContent) UnoRuntime.queryInterface(
	             XTextContent.class, xRefMark);
	        xText.insertTextContent (xTextCursor, xContent, false);
		    logger.warn("Bookmark insertado: " + bookmark);
		}
		catch(Exception e)
		{
			throw new ISPACException(e);
		}
	}

	public static int getNumPages(XComponent xComponent)  throws ISPACException
	{
		int nPages = 0;
		try
		{
		    XTextDocument xTextDocument = (XTextDocument)UnoRuntime.queryInterface(XTextDocument.class, xComponent);
		    XText xText = xTextDocument.getText();
		    XTextCursor xTextCursor = xText.createTextCursor();
		    xTextCursor.gotoRange(xText.getEnd(),false);

			XModel xModel = (XModel)UnoRuntime.queryInterface(XModel.class, xComponent); 
			XController xController = xModel.getCurrentController(); 
			XTextViewCursorSupplier xViewCursorSupplier = (XTextViewCursorSupplier)UnoRuntime.queryInterface(XTextViewCursorSupplier.class, xController); 
			XTextViewCursor xViewCursor = xViewCursorSupplier.getViewCursor(); 
			xViewCursor.gotoRange(xTextCursor, false);
		    
	        XPageCursor xPageCursor = (XPageCursor)UnoRuntime.queryInterface(XPageCursor.class, xViewCursor);
	        nPages = (int)xPageCursor.getPage();
		}
		catch(Exception e)
		{
			throw new ISPACException(e);
		}
		return nPages;
	}
}