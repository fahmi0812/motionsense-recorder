package id.ac.pcr.motionsense.recorder;

/** Label UI (Bahasa Indonesia) -> kode label yang dipakai training & CHECK constraint Supabase. */
public final class Labels {
  public static final String[] DISPLAY = {"berjalan", "naik_tangga", "turun_tangga", "duduk", "berdiri", "berbaring"};
  public static final String[] CODE = {"WALKING", "WALKING_UPSTAIRS", "WALKING_DOWNSTAIRS", "SITTING", "STANDING", "LAYING"};
  private Labels() {}
}
