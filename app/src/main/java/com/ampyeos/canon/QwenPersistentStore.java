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
    public Uri find(String exactName){
        try(Cursor c=resolver.query(collection(),
            new String[]{MediaStore.MediaColumns._ID,MediaStore.MediaColumns.SIZE},
            MediaStore.MediaColumns.DISPLAY_NAME+"=? AND "+
                MediaStore.MediaColumns.RELATIVE_PATH+"=?",
            new String[]{exactName,PATH},null)){
            if(c!=null&&c.moveToFirst())
                return ContentUris.withAppendedId(collection(),c.getLong(0));
        }catch(Exception ignored){}
        return null;
    }
    public Uri create(String name)throws IOException {
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,PATH);
        // Visible partial rows allow resumed downloads even if Android kills app.
        Uri result=resolver.insert(collection(),values);
        if(result==null)throw new IOException("Android non può creare il modello in Download");
        return result;
    }
    public long length(Uri uri){
        if(uri==null)return -1;
        try(ParcelFileDescriptor fd=resolver.openFileDescriptor(uri,"r")){
            return fd==null?-1:fd.getStatSize();
        }catch(Exception e){return -1;}
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
        boolean success=false;
        try(InputStream in=new java.io.FileInputStream(verified);
            ParcelFileDescriptor fd=writable(target);
            FileOutputStream out=new FileOutputStream(fd.getFileDescriptor())) {
            byte[] bytes=new byte[128*1024];long copied=0;int read;
            while((read=in.read(bytes))!=-1){
                copied+=read;if(copied>expected)throw new IOException("Dimensione modello eccessiva");
                out.write(bytes,0,read);
            }
            out.flush();
            if(copied!=expected)throw new IOException("Copia modello non completa");
        }catch(IOException e){discardOwnPartial(target);throw e;}
        finish(target,name);
        success=true;
        return target;
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
