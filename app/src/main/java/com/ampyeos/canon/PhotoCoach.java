package com.ampyeos.canon;

/**
 * Profile-aware photographic coaching. These are guides, not camera settings;
 * no automatic camera parameter change is performed.
 */
public final class PhotoCoach {
    public enum Profile {
        AUTO("AUTO", "Bilancia esposizione, nitidezza e soggetto"),
        RITRATTO("RITRATTO", "Occhi a fuoco e sfondo poco invasivo"),
        PAESAGGIO("PAESAGGIO", "Profondità, orizzonte, gamma tonale"),
        STREET("STREET", "Momento, linee, soggetto in movimento"),
        NOTTE("NOTTE", "Stabilità, alte luci, rumore"),
        MACRO("MACRO", "Fuoco preciso e profondità di campo");

        public final String label;
        public final String description;
        Profile(String name, String desc) { label=name; description=desc; }
        public static Profile fromName(String value) {
            for(Profile p : values()) if(p.name().equals(value)) return p;
            return AUTO;
        }
        public static String[] labels() {
            Profile[] all=values();
            String[] result=new String[all.length];
            for(int i=0;i<all.length;i++) result[i]=all[i].label;
            return result;
        }
    }

    private PhotoCoach() {}

    /** A short prioritized recommendation based on observed luminance and texture. */
    public static String suggest(PhotoAnalyzer.Result r,Profile p) {
        if(r==null) return p.description;
        if(r.highlights>12 && r.shadows>24)
            return "Contrasto estremo: proteggi le alte luci; valuta bracketing o una luce di riempimento.";
        if(r.highlights>12)
            return "Alte luci prossime al clipping: riduci un po' l'esposizione e ricontrolla l'istogramma.";
        if(r.brightness>215)
            return "Scena molto luminosa: controlla esposizione e dettagli nelle zone chiare.";
        if(r.shadows>45 && p!=Profile.NOTTE)
            return "Ombre chiuse: cerca luce sul soggetto o valuta una compensazione positiva.";
        if(r.detail<12 && p!=Profile.NOTTE)
            return "Microcontrasto ridotto: controlla il fuoco e riduci possibili vibrazioni.";
        switch(p) {
            case RITRATTO:
                return r.shadows>22
                    ? "Ritratto: ammorbidisci la luce sul volto e verifica il fuoco sugli occhi."
                    : "Ritratto: privilegia gli occhi a fuoco, luce morbida e sfondo pulito.";
            case PAESAGGIO:
                return r.contrast<42
                    ? "Paesaggio: prova luce radente per dare profondità; controlla le linee dell'orizzonte."
                    : "Paesaggio: prova un primo piano interessante e verifica la profondità di campo.";
            case STREET:
                return "Street: anticipa il momento, controlla il tempo di scatto e cerca linee che guidino l'occhio.";
            case NOTTE:
                return r.highlights>5
                    ? "Notte: proteggi insegne e luci puntiformi; valuta treppiede e scatti multipli."
                    : "Notte: stabilizza la fotocamera e verifica il tempo per il movimento del soggetto.";
            case MACRO:
                return "Macro: controlla il piano del fuoco; valuta focus bracketing e luce diffusa.";
            default:
                if(r.contrast<42) return "Contrasto basso: separa il soggetto dallo sfondo con posizione o luce.";
                return "Buona base tecnica: prova una seconda inquadratura cambiando punto di vista.";
        }
    }
}
