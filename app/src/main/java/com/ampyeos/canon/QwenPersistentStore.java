package com.ampyeos.canon;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

/**
 * Persistent Qwen GGUF files in Download/AmpyEOS/Qwen3-VL using MediaStore.
 * No all-files permission: files survive application uninstall and APK upgrades.
 * Model files are accessed through content:// descriptors (scoped storage),
 * never via a guessed external-storage file path.
 */
public final class QwenPersistentStore {
    public static final String PATH=Environment.DIRECTORY_DOWNLOADS+"/AmpyEOS/Qwen3-VL/";
    private final ContentResolver resolver;
    private final Context app;
    public QwenPersistentStore(Context context){
        app=context.getApplicationContext();
        resolver=app.getContentResolver();
    }
    private static Uri collection(){
        return MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    }
    private volatile String lastError="";
    public String lastError(){return lastError;}
    /**
     * The provider may leave older partial rows after a process kill.
     * Pick the newest matching row rather than an arbitrary old duplicate.
     */
    public Uri find(String exactName){
        try(Cursor c=resolver.query(collection(),
            new String[]{MediaStore.MediaColumns._ID,MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.RELATIVE_PATH},
            MediaStore.MediaColumns.DISPLAY_NAME+"=? AND "+
                MediaStore.MediaColumns.RELATIVE_PATH+"=?",
            new String[]{exactName,PATH},
            MediaStore.MediaColumns.DATE_MODIFIED+" DESC")){
            if(c!=null){
                while(c.moveToNext()){
                    Uri candidate=ContentUris.withAppendedId(collection(),c.getLong(0));
                    // Avoid returning an inaccessible row and silently re-downloading.
                    try(ParcelFileDescriptor fd=resolver.openFileDescriptor(candidate,"r")){
                        if(fd!=null)return candidate;
                    }catch(Exception ignored){}
                }
            }
            lastError="";
        }catch(Exception ex){lastError=ex.getClass().getSimpleName()+
            ": "+(ex.getMessage()==null?"MediaStore non accessibile":ex.getMessage());}
        return null;
    }
    public Uri create(String name)throws IOException {
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,PATH);
        values.put(MediaStore.MediaColumns.IS_PENDING,0);
        // A visible partial .part survives closing the editor or system kill.
        Uri result=resolver.insert(collection(),values);
        if(result==null)throw new IOException("Android non può creare il modello in Download");
        return result;
    }
    public long length(Uri uri){
        if(uri==null)return -1;
        try(ParcelFileDescriptor fd=resolver.openFileDescriptor(uri,"r")){
            if(fd!=null&&fd.getStatSize()>=0)return fd.getStatSize();
        }catch(Exception ignored){}
        // Some Android SAF document providers do not report descriptor size.
        try(Cursor c=resolver.query(uri,new String[]{OpenableColumns.SIZE},
            null,null,null)){
            if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getLong(0);
        }catch(Exception ignored){}
        return -1;
    }
    public InputStream read(Uri uri)throws IOException {
        InputStream input=resolver.openInputStream(uri);
        if(input==null)throw new IOException("Il file Download non è leggibile");
        return input;
    }
    public ParcelFileDescriptor descriptor(Uri uri)throws IOException {
        ParcelFileDescriptor fd=resolver.openFileDescriptor(uri,"r");
        if(fd==null)throw new IOException("Non posso aprire il modello da Download");
        return fd;
    }
    /** Caller uses an open file channel with random access for HTTP Range resume. */
    public ParcelFileDescriptor writable(Uri uri)throws IOException {
        ParcelFileDescriptor fd=resolver.openFileDescriptor(uri,"rw");
        if(fd==null)throw new IOException("Non posso scrivere in Download");
        return fd;
    }
    public void finish(Uri uri,String name)throws IOException {
        ContentValues update=new ContentValues();
        update.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        if(resolver.update(uri,update,null,null)<1)
            throw new IOException("Android non ha potuto finalizzare il modello in Download");
    }
    public void discardOwnPartial(Uri uri) {
        if(uri==null)return;
        try{resolver.delete(uri,null,null);}catch(Exception ignored){}
    }
    public Uri publish(File verified,String name,long expected)throws IOException {
        if(verified==null||!verified.isFile()||verified.length()!=expected)
            throw new IOException("Modello privato non completo");
        Uri exists=find(name);
        if(exists!=null && length(exists)==expected) return exists;
        Uri target=create(name+".publishing");
        try(InputStream in=new java.io.FileInputStream(verified);
            ParcelFileDescriptor fd=writable(target);
            FileOutputStream out=new FileOutputStream(fd.getFileDescriptor())) {
            byte[] bytes=new byte[128*1024];long copied=0;int read;
            while((read=in.read(bytes))!=-1){
                if(Thread.currentThread().isInterrupted())
                    throw new IOException("Copia interrotta: il vecchio modello rimane intatto");
                copied+=read;
                if(copied>expected)throw new IOException("Dimensione modello eccessiva");
                out.write(bytes,0,read);
            }
            out.flush();
            if(copied!=expected)throw new IOException("Copia modello non completa");
        }catch(IOException e){discardOwnPartial(target);throw e;}
        finish(target,name);
        return target;
    }
    public String publicModelFilesStatus(){
        StringBuilder status=new StringBuilder("Memoria condivisa: "+
            "Download/AmpyEOS/Qwen3-VL/\\n");
        String[] names={QwenModelManager.MODEL,QwenModelManager.PROJECTOR};
        for(String name:names){
            Uri full=find(name),partial=find(name+".part");
            long complete=length(full),staging=length(partial);
            status.append(name).append(": ");
            if(complete>0)status.append(complete/1048576).append(" MiB (file finale)");
            else status.append("NON PRESENTE");
            if(staging>=0)status.append(" · parziale ")
                .append(staging/1048576).append(" MiB");
            status.append('\\n');
        }
        if(!lastError.isEmpty())status.append("MediaStore: ").append(lastError).append('\\n');
        status.append("Se i file esistono nel gestore Download ma qui non compaiono, "+
            "selezionali con IMPORTA GGUF DA DOWNLOAD per concedere accesso.");
        return status.toString();
    }

    public boolean isDownloadLocation(Uri uri){
        if(uri==null)return false;
        String value=uri.toString();
        return value.startsWith("content://media/");
    }
    public static final class OpenModels implements AutoCloseable {
        public final ParcelFileDescriptor language,projector;
        OpenModels(ParcelFileDescriptor language,ParcelFileDescriptor projector){
            this.language=language;this.projector=projector;
        }
        public String languagePath(){return "/proc/self/fd/"+language.getFd();}
        public String projectorPath(){return "/proc/self/fd/"+projector.getFd();}
        @Override public void close()throws IOException {
            try{projector.close();}finally{language.close();}
        }
    }
    public OpenModels openPair(Uri languageUri,Uri projectorUri)throws IOException {
        ParcelFileDescriptor model=descriptor(languageUri);
        try{
            ParcelFileDescriptor projector=descriptor(projectorUri);
            return new OpenModels(model,projector);
        }catch(Exception ex){
            model.close();
            throw ex instanceof IOException?(IOException)ex:new IOException(ex);
        }
    }
}
