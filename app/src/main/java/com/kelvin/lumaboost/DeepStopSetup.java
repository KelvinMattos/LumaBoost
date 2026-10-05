package com.kelvin.lumaboost;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

/** Explica o uso da acessibilidade antes de pedir a ativação (divulgação exigida pela Play Store). */
final class DeepStopSetup {
    private DeepStopSetup() {
    }

    static void show(final Activity activity) {
        new AlertDialog.Builder(activity, Ui.dark()
                ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Ativar o assistente do Luma")
                .setMessage("O Android não deixa um app fechar outros apps nem limpar o lixo deles sozinho. "
                        + "Com o assistente, o Luma faz isso por você, tocando nos botões \"Forçar parada\" e \"Limpar cache\" "
                        + "das configurações, como você faria com o dedo. Para isso ele usa a Acessibilidade do Android.\n\n"
                        + "• Só trabalha quando você pede.\n"
                        + "• Nunca toca em \"Limpar armazenamento\": suas contas e conversas ficam a salvo.\n"
                        + "• Não lê, não guarda e não envia nada da sua tela.\n"
                        + "• Mensageiros, alarmes e os apps que você proteger nunca são fechados.\n\n"
                        + "Na próxima tela, toque em \"Luma Boost\" e ligue a chave.")
                .setPositiveButton("Concordo, ativar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openSettings(activity);
                    }
                })
                .setNegativeButton("Agora não", null)
                .show();
    }

    private static void openSettings(Activity activity) {
        Guide.open(activity, Guide.ASSISTANT);
    }
}
