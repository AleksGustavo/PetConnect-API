package com.petconnect.api.migration.support;

/**
 * Decide qual URL de foto sobrevive à migração.
 *
 * <p>O bucket do Firebase Storage foi desativado (billing encerrado): toda URL
 * {@code firebasestorage.googleapis.com} é considerada perdida. Só URLs do
 * Cloudinary passam. Placeholders são descartados.
 */
public final class PhotoResolver {

    private PhotoResolver() {
    }

    /**
     * @param candidates URLs em ordem de preferência (ex.: {@code foto}, {@code imagemUrl}, {@code photoURL})
     * @return url mantida (ou {@code null}) + aviso opcional
     */
    public static Result resolve(String... candidates) {
        boolean sawStorage = false;
        boolean sawPlaceholder = false;

        for (String c : candidates) {
            String url = LegacyValues.trimToNull(c);
            if (url == null) {
                continue;
            }
            String lower = url.toLowerCase();
            if (lower.contains("res.cloudinary.com")) {
                return new Result(url, null);
            }
            if (lower.contains("firebasestorage.googleapis.com")) {
                sawStorage = true;
            } else if (lower.contains("placehold")) {
                sawPlaceholder = true;
            }
        }

        if (sawStorage) {
            return new Result(null, "storage-image-lost");
        }
        if (sawPlaceholder) {
            return new Result(null, "placeholder-image");
        }
        return new Result(null, null);
    }

    public record Result(String url, String warning) {
    }
}
