import type { LegalContent } from './content';

// Authored in French, like the rest of the interface. The bracketed passages are facts only
// the publisher can state (#74): they are filled in before the service is promoted.
const TODO = (what: string) => `[À compléter : ${what}]`;

const content: LegalContent = {
  terms: {
    title: "Conditions générales d'utilisation",
    updated: 'Dernière mise à jour : 21 novembre 2026',
    sections: [
      {
        heading: 'Objet du service',
        paragraphs: [
          "Librarius est une bibliothèque personnelle en ligne pour les livres et les mangas : tu y ranges ta collection, suis tes lectures, tiens ta liste d'envies et, si tu le souhaites, partages ton activité avec les lecteurs que tu suis.",
          "En créant un compte puis en acceptant ces conditions, tu t'engages à les respecter. Si tu ne les acceptes pas, n'utilise pas le service.",
        ],
      },
      {
        heading: 'Ton compte',
        paragraphs: [
          "Le compte est personnel. Tu es responsable de la confidentialité de ton mot de passe et de ce qui est fait depuis ton compte.",
          "Le nom affiché, la photo de profil, les avis et les commentaires que tu publies doivent respecter la loi et les autres lecteurs : pas de contenu illicite, haineux, diffamatoire ou portant atteinte aux droits d'autrui.",
        ],
      },
      {
        heading: 'Contenus et catalogue',
        paragraphs: [
          "Tes notes, avis et fiches de lecture restent les tiens. En les rendant visibles à d'autres lecteurs, tu autorises leur affichage dans le service, et seulement là.",
          "Les fiches du catalogue (titres, auteurs, couvertures) proviennent de sources publiques — Open Library, AniList, la BnF — et des corrections des lecteurs. Leur exactitude n'est pas garantie.",
        ],
      },
      {
        heading: 'Imports depuis d’autres sites',
        paragraphs: [
          "Les imports Booknode, Babelio et Mangacollec lisent ta propre bibliothèque, dans ton propre navigateur et avec ta propre session, puis t'en font enregistrer un fichier que tu importes. Aucun serveur de Librarius ne se connecte à ces sites à ta place.",
          "Tu restes tenu de respecter les conditions d'utilisation de ces sites. Ces imports sont proposés pour récupérer tes propres données ; ils peuvent cesser de fonctionner si ces sites changent.",
        ],
      },
      {
        heading: 'Modération',
        paragraphs: [
          "Un contenu signalé peut être masqué ou supprimé. Un compte qui enfreint ces conditions peut être suspendu ou supprimé, après avertissement sauf urgence.",
        ],
      },
      {
        heading: 'Disponibilité et garanties',
        paragraphs: [
          "Le service est fourni gratuitement et « en l'état », sans garantie de disponibilité ni d'absence d'erreur. Des interruptions peuvent survenir, notamment lors des mises à jour.",
          "Exporte régulièrement ta bibliothèque (Réglages › Données et compte) si elle compte pour toi.",
        ],
      },
      {
        heading: 'Fin du service',
        paragraphs: [
          "Tu peux supprimer ton compte à tout moment depuis Réglages › Données et compte ; tes données sont alors effacées comme le décrit la politique de confidentialité.",
          "Si le service devait fermer, les lecteurs en seraient prévenus suffisamment tôt pour exporter leurs données.",
        ],
      },
      {
        heading: 'Modifications',
        paragraphs: [
          "Ces conditions peuvent évoluer. Toute modification importante te sera présentée à ta prochaine connexion, et devra être acceptée pour continuer à utiliser le service.",
          `Ces conditions sont régies par le droit français. Contact : ${TODO('adresse e-mail de contact')}.`,
        ],
      },
    ],
  },
  privacy: {
    title: 'Politique de confidentialité',
    updated: 'Dernière mise à jour : 21 novembre 2026',
    sections: [
      {
        heading: 'Responsable du traitement',
        paragraphs: [
          `${TODO('nom ou raison sociale de l’éditeur')}, joignable à ${TODO('adresse e-mail de contact')}.`,
        ],
      },
      {
        heading: 'Données collectées',
        paragraphs: [
          "Ton compte : adresse e-mail, identifiant de connexion, nom affiché, et, si tu les renseignes, ta photo de profil, ta langue, ton fuseau horaire et ton pays. Avec Google ou Apple, seule l'adresse e-mail est importée.",
          "Ta bibliothèque : les livres et mangas que tu ajoutes, leurs statuts, ta progression, tes notes, avis, fiches de lecture, catégories, liste d'envies, objectif annuel et succès.",
          "Ta vie sociale dans l'app : les lecteurs que tu suis, ceux qui te suivent, les membres que tu bloques, les commentaires et les notifications.",
          "Ce que tu nous envoies : les signalements de problème, avec le journal technique de la session qu'ils joignent, et les signalements de contenu.",
          "Des journaux techniques (identifiant technique, requêtes, erreurs) nécessaires au fonctionnement et à la sécurité. Aucun cookie publicitaire, aucun traceur tiers, aucune mesure d'audience.",
        ],
      },
      {
        heading: 'Pourquoi, et sur quelle base',
        paragraphs: [
          "Fournir le service que tu as demandé en créant ton compte (exécution du contrat) : tenir ta bibliothèque, afficher ton profil aux lecteurs que tu autorises, t'envoyer les notifications que tu as activées.",
          "Le sécuriser et le faire fonctionner (intérêt légitime) : journaux techniques, limitation des abus, modération des contenus signalés.",
          "Rien n'est vendu, loué ni utilisé à des fins publicitaires.",
        ],
      },
      {
        heading: 'Sous-traitants et destinataires',
        paragraphs: [
          `Hébergement : ${TODO('nom et adresse de l’hébergeur, pays des serveurs')}. La connexion (Keycloak) et la base de données tournent sur ces mêmes serveurs.`,
          "Google et Apple, uniquement si tu choisis de te connecter avec eux.",
          "Google Fonts fournit les polices de l'interface : ton navigateur les télécharge depuis les serveurs de Google, qui voient alors l'adresse IP de ton appareil.",
          "Les catalogues Open Library, AniList et la BnF reçoivent les recherches de titres faites par le serveur, sans aucune donnée te concernant. Les couvertures peuvent être chargées directement depuis leurs serveurs, qui voient alors l'adresse IP de ton appareil.",
          "Les autres lecteurs ne voient de ton activité que ce que tes réglages de visibilité leur ouvrent.",
        ],
      },
      {
        heading: 'Durée de conservation',
        paragraphs: [
          "Tes données sont conservées tant que ton compte existe.",
          "À la suppression du compte, ta connexion et toutes tes données sont effacées immédiatement. Les sauvegardes chiffrées, conservées hors du serveur pour reconstruire le service après un sinistre, en gardent une copie jusqu'à six mois avant d'être purgées.",
          "Les journaux techniques ne contiennent que ton identifiant technique et suivent la rotation de l'hébergement.",
        ],
      },
      {
        heading: 'Tes droits',
        paragraphs: [
          "Tu peux accéder à tes données, les rectifier, t'opposer à un traitement, en demander la limitation, la portabilité ou l'effacement.",
          "Dans l'application : Réglages › Profil pour rectifier, Réglages › Données et compte pour exporter toute ta bibliothèque (portabilité) ou supprimer ton compte (effacement).",
          `Pour tout autre demande : ${TODO('adresse e-mail de contact')}. Tu peux aussi saisir la CNIL (www.cnil.fr).`,
        ],
      },
      {
        heading: 'Sécurité',
        paragraphs: [
          "Les échanges sont chiffrés (HTTPS), chaque donnée est rattachée à ton compte et n'est lue qu'en ton nom, et les sauvegardes sont chiffrées.",
        ],
      },
    ],
  },
  notice: {
    title: 'Mentions légales',
    updated: 'Dernière mise à jour : 21 novembre 2026',
    sections: [
      {
        heading: 'Éditeur',
        paragraphs: [
          `${TODO('nom ou raison sociale, statut, adresse postale')}.`,
          `Directeur de la publication : ${TODO('nom')}.`,
          `Contact : ${TODO('adresse e-mail de contact')}.`,
        ],
      },
      {
        heading: 'Hébergeur',
        paragraphs: [`${TODO('raison sociale, adresse et téléphone de l’hébergeur')}.`],
      },
      {
        heading: 'Données du catalogue',
        paragraphs: [
          "Fiches et couvertures issues d'Open Library, d'AniList et de la Bibliothèque nationale de France, sous leurs licences respectives.",
        ],
      },
    ],
  },
};

export default content;
